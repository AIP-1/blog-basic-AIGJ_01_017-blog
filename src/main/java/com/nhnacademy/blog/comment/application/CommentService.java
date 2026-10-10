package com.nhnacademy.blog.comment.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.notification.application.NotificationService;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 보기·쓰기·지우기 (T044, CMT-01, CMT-02).
 * 댓글은 글에 딸려 있어서, 글을 볼 수 없는 사람에게는 댓글도 없다(글과 같은 404·403 판단, PostReadService.readable).
 * 답글은 한 단계(CMT-05, 스텝 7), 본인 댓글 고치기(CMT-03, 스텝 14), 비밀댓글(CMT-06, 스텝 17).
 * 보는 사람 기준의 모양(비밀·삭제된 자리·숨김)은 방명록과 같은 CommentViews가 정한다.
 */
@Service
public class CommentService {

    public static final int PAGE_SIZE = 20;

    private static final Sort WRITTEN_ORDER = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PostReadService postReadService;
    private final CommentViews commentViews;
    private final NotificationService notificationService;
    private final Clock clock;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository,
                          MemberRepository memberRepository,
                          PostReadService postReadService, CommentViews commentViews,
                          NotificationService notificationService, Clock clock) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
        this.postReadService = postReadService;
        this.commentViews = commentViews;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * 작성순 20개씩 더보기. (작성 시각, id) 커서라 읽는 중에 새 댓글이 달려도 겹치거나 빠지지 않는다.
     * 묶음은 최상위 댓글 기준이고, 답글(CMT-05)은 부모 안에 작성순으로 모두 붙인다.
     * 지운 댓글은 빼되, 지우지 않은 답글이 있는 부모는 "삭제된 댓글입니다" 자리로 남긴다.
     */
    @Transactional(readOnly = true)
    public CommentPage list(Blog blog, Long postId, Long viewerId, TimeIdCursor cursor) {
        Post post = postReadService.readable(blog, postId, viewerId);
        Specification<Comment> condition = (root, query, cb) -> {
            Subquery<Long> liveReply = query.subquery(Long.class);
            Root<Comment> reply = liveReply.from(Comment.class);
            liveReply.select(reply.get("id")).where(
                    cb.equal(reply.get("parent"), root),
                    cb.isNull(reply.get("deletedAt")));
            return cb.and(
                    cb.equal(root.get("post").get("id"), post.getId()),
                    cb.isNull(root.get("parent")),
                    cb.or(cb.isNull(root.get("deletedAt")), cb.exists(liveReply)));
        };
        if (cursor != null) {
            condition = condition.and((root, query, cb) -> cb.or(
                    cb.greaterThan(root.get("createdAt"), cursor.time()),
                    cb.and(cb.equal(root.get("createdAt"), cursor.time()), cb.greaterThan(root.get("id"), cursor.id()))));
        }
        List<Comment> parents = commentRepository.findBy(condition,
                query -> query.sortBy(WRITTEN_ORDER).project("member").limit(PAGE_SIZE + 1).all());
        List<Comment> replies = parents.isEmpty() ? List.of() : commentRepository.findBy(
                (root, query, cb) -> cb.and(
                        root.get("parent").in(parents),
                        cb.isNull(root.get("deletedAt"))),
                query -> query.sortBy(WRITTEN_ORDER).project("member").all());

        List<CommentView> views = commentViews.threads(parents, replies, blog, viewerId);
        return new CommentPage(views, commentRepository.countByPostIdAndDeletedAtIsNull(post.getId()));
    }

    /**
     * 댓글을 쓸 수 있는 글인가. 상태 코드 순서대로: 볼 수 없는 글 404(구독자 공개 403) → 비회원 401 → 댓글을 막은 글 403.
     * 입력 검증(400)은 이 다음에 컨트롤러가 한다.
     */
    @Transactional(readOnly = true)
    public Post writablePost(Blog blog, Long postId, LoginMember member) {
        Post post = postReadService.readable(blog, postId, member == null ? null : member.id());
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!post.isCommentAllowed()) {
            throw new BusinessException(ErrorCode.COMMENTS_DISABLED);
        }
        return post;
    }

    /** 쓰기. 글의 댓글 수도 같은 트랜잭션에서 늘린다(새로고침해도 실제 값과 같게, data-model). */
    @Transactional
    public CommentView write(Blog blog, Long postId, LoginMember member, String content, Long parentId,
                             boolean secret) {
        Post post = writablePost(blog, postId, member);
        // 댓글 INSERT(외래 키 공유 잠금) 뒤 댓글 수 UPDATE(배타 잠금)가 동시에 엇갈리면 데드락이라 글 행부터 잠근다
        postRepository.lockById(post.getId());
        Member author = memberRepository.getReferenceById(member.id());
        Comment comment = commentRepository.save(parentId == null
                ? Comment.write(post, author, content.trim(), secret)
                : Comment.reply(parentOf(post, parentId), author, content.trim(), secret));
        postRepository.addCommentCount(post.getId(), 1);
        Comment saved = commentRepository.findBy(
                (root, query, cb) -> cb.equal(root.get("id"), comment.getId()),
                query -> query.project("member").first()).orElseThrow();
        notificationService.commented(saved);
        return commentViews.one(saved, blog, member.id());
    }

    /**
     * 답글을 달 댓글 (CMT-05). 같은 글의, 지우지 않은 최상위 댓글이어야 한다. 답글의 답글은 400(한 단계).
     */
    private Comment parentOf(Post post, Long parentId) {
        Comment parent = commentRepository.findById(parentId)
                .filter(found -> found.getPost().getId().equals(post.getId()) && !found.isDeleted())
                .orElseThrow(() -> BusinessException.invalidField("parentId", "답글을 달 댓글을 찾을 수 없습니다."));
        if (parent.getParent() != null) {
            throw BusinessException.invalidField("parentId", "답글에는 답글을 달 수 없습니다.");
        }
        return parent;
    }

    /**
     * 지우기. 작성자 본인(CMT-01)과 블로그 주인(CMT-02)만. 없거나, 이 블로그 글의 댓글이 아니거나,
     * 그 글을 볼 수 없으면 404 → 비회원 401 → 권한 없음 403.
     */
    @Transactional
    public void delete(Blog blog, Long commentId, LoginMember member) {
        Comment comment = visibleComment(blog, commentId, member);
        Long viewerId = member.id();
        if (!comment.isWrittenBy(viewerId) && !blog.isOwnedBy(viewerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        postRepository.lockById(comment.getPost().getId());
        comment.delete(LocalDateTime.now(clock));
        // 댓글 수 UPDATE가 영속성 컨텍스트를 비우므로(clearAutomatically) 삭제를 먼저 DB에 보낸다
        commentRepository.flush();
        postRepository.addCommentCount(comment.getPost().getId(), -1);
    }

    /**
     * 고칠 댓글 (CMT-03). 상태 코드 순서는 지우기와 같다: 없음·볼 수 없음 404 → 비회원 401 → 본인이 아님 403.
     * 관리자가 숨긴 댓글은 본인도 고칠 수 없다(403, ADMIN-03 "작성자는 수정할 수 없고 삭제는 할 수 있다").
     * 입력 검증(400)은 이 다음에 컨트롤러가 한다.
     */
    @Transactional(readOnly = true)
    public void checkEditable(Blog blog, Long commentId, LoginMember member) {
        editable(blog, commentId, member);
    }

    /** 내용 고치기 (CMT-03). 글의 수정 시각이나 댓글 수는 바뀌지 않는다. 고친 시각은 댓글의 updatedAt으로 보인다. */
    @Transactional
    public CommentView edit(Blog blog, Long commentId, LoginMember member, String content) {
        Comment comment = editable(blog, commentId, member);
        comment.edit(content.trim());
        // updatedAt(@LastModifiedDate)이 응답에 들어가도록 UPDATE를 먼저 보낸다
        commentRepository.flush();
        return commentViews.one(comment, blog, member.id());
    }

    private Comment editable(Blog blog, Long commentId, LoginMember member) {
        Comment comment = visibleComment(blog, commentId, member);
        if (!comment.isWrittenBy(member.id()) || comment.isBlinded()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return comment;
    }

    /** 이 블로그 글의, 지우지 않은, 글을 볼 수 있는 댓글. 그다음 로그인했는지 본다(404 → 401 순서). */
    private Comment visibleComment(Blog blog, Long commentId, LoginMember member) {
        Comment comment = commentRepository.findWithPostById(commentId)
                .filter(found -> !found.isDeleted())
                .filter(found -> found.getPost().getBlog().getId().equals(blog.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        postReadService.readable(blog, comment.getPost().getId(), member == null ? null : member.id());
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return comment;
    }

}
