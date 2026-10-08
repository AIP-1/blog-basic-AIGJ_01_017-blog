package com.nhnacademy.blog.comment.application;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.blog.application.PrimaryBlogAddresses;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 보기·쓰기·지우기 (T044, CMT-01, CMT-02).
 * 댓글은 글에 딸려 있어서, 글을 볼 수 없는 사람에게는 댓글도 없다(글과 같은 404·403 판단, PostReadService.readable).
 * 답글(CMT-05)·비밀댓글 쓰기(CMT-06)·수정(CMT-03)은 스텝 7 이후다.
 */
@Service
public class CommentService {

    public static final int PAGE_SIZE = 20;

    private static final Sort WRITTEN_ORDER = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final PostReadService postReadService;
    private final PrimaryBlogAddresses primaryBlogAddresses;
    private final Clock clock;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository,
                          MemberRepository memberRepository, ModerationLogRepository moderationLogRepository,
                          PostReadService postReadService, PrimaryBlogAddresses primaryBlogAddresses, Clock clock) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.postReadService = postReadService;
        this.primaryBlogAddresses = primaryBlogAddresses;
        this.clock = clock;
    }

    /** 작성순 20개씩 더보기. (작성 시각, id) 커서라 읽는 중에 새 댓글이 달려도 겹치거나 빠지지 않는다. */
    @Transactional(readOnly = true)
    public CommentPage list(Blog blog, Long postId, Long viewerId, TimeIdCursor cursor) {
        Post post = postReadService.readable(blog, postId, viewerId);
        Specification<Comment> condition = (root, query, cb) -> cb.and(
                cb.equal(root.get("post").get("id"), post.getId()),
                cb.isNull(root.get("deletedAt")));
        if (cursor != null) {
            condition = condition.and((root, query, cb) -> cb.or(
                    cb.greaterThan(root.get("createdAt"), cursor.time()),
                    cb.and(cb.equal(root.get("createdAt"), cursor.time()), cb.greaterThan(root.get("id"), cursor.id()))));
        }
        List<Comment> comments = commentRepository.findBy(condition,
                query -> query.sortBy(WRITTEN_ORDER).project("member").limit(PAGE_SIZE + 1).all());
        Map<Long, String> addresses = primaryBlogAddresses.of(
                comments.stream().map(comment -> comment.getMember().getId()).distinct().toList(), viewerId);
        List<CommentView> views = comments.stream()
                .map(comment -> view(comment, blog, viewerId, addresses.get(comment.getMember().getId())))
                .toList();
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
    public CommentView write(Blog blog, Long postId, LoginMember member, String content) {
        Post post = writablePost(blog, postId, member);
        Comment comment = commentRepository.save(
                Comment.write(post, memberRepository.getReferenceById(member.id()), content.trim(), false));
        postRepository.addCommentCount(post.getId(), 1);
        Comment saved = commentRepository.findBy(
                (root, query, cb) -> cb.equal(root.get("id"), comment.getId()),
                query -> query.project("member").first()).orElseThrow();
        return view(saved, blog, member.id(), primaryBlogAddresses.of(List.of(member.id()), member.id())
                .get(member.id()));
    }

    /**
     * 지우기. 작성자 본인(CMT-01)과 블로그 주인(CMT-02)만. 없거나, 이 블로그 글의 댓글이 아니거나,
     * 그 글을 볼 수 없으면 404 → 비회원 401 → 권한 없음 403.
     */
    @Transactional
    public void delete(Blog blog, Long commentId, LoginMember member) {
        Long viewerId = member == null ? null : member.id();
        Comment comment = commentRepository.findWithPostById(commentId)
                .filter(found -> !found.isDeleted())
                .filter(found -> found.getPost().getBlog().getId().equals(blog.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        postReadService.readable(blog, comment.getPost().getId(), viewerId);
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!comment.isWrittenBy(viewerId) && !blog.isOwnedBy(viewerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.delete(LocalDateTime.now(clock));
        // 댓글 수 UPDATE가 영속성 컨텍스트를 비우므로(clearAutomatically) 삭제를 먼저 DB에 보낸다
        commentRepository.flush();
        postRepository.addCommentCount(comment.getPost().getId(), -1);
    }

    private CommentView view(Comment comment, Blog blog, Long viewerId, String authorAddress) {
        boolean author = comment.isWrittenBy(viewerId);
        boolean blogOwner = blog.isOwnedBy(viewerId);
        CommentView.State state;
        Map<String, String> blind = null;
        if (comment.isBlinded()) {
            // 작성자 본인에게는 내용과 숨김 사유를 보여 주고, 다른 사람에게는 자리만 (ADMIN-03)
            state = author ? CommentView.State.NORMAL : CommentView.State.BLINDED;
            blind = author ? blindReason(comment) : null;
        } else if (comment.isSecret() && !author && !blogOwner) {
            state = CommentView.State.SECRET;   // 글 주인과 작성자만 본다 (CMT-06)
        } else {
            state = CommentView.State.NORMAL;
        }
        return new CommentView(comment, state, authorAddress, author || blogOwner, blind);
    }

    private Map<String, String> blindReason(Comment comment) {
        return moderationLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
                        ModerationTargetType.COMMENT, comment.getId(), ModerationAction.BLIND)
                .map(log -> Map.of("reason", log.getReason().name(), "reasonMessage", log.getReason().getMessage()))
                .orElse(Map.of());
    }

}
