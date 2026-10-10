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
import com.nhnacademy.blog.image.application.ProfileImages;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 보기·쓰기·지우기 (T044, CMT-01, CMT-02).
 * 댓글은 글에 딸려 있어서, 글을 볼 수 없는 사람에게는 댓글도 없다(글과 같은 404·403 판단, PostReadService.readable).
 * 답글은 한 단계(CMT-05, 스텝 7). 비밀댓글 쓰기(CMT-06)·수정(CMT-03)은 뒤 스텝이다.
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
    private final ProfileImages profileImages;
    private final Clock clock;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository,
                          MemberRepository memberRepository, ModerationLogRepository moderationLogRepository,
                          PostReadService postReadService, PrimaryBlogAddresses primaryBlogAddresses,
                          ProfileImages profileImages, Clock clock) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.postReadService = postReadService;
        this.primaryBlogAddresses = primaryBlogAddresses;
        this.profileImages = profileImages;
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

        List<Member> authors = Stream.concat(parents.stream(), replies.stream())
                .map(Comment::getMember).distinct().toList();
        Map<Long, String> addresses = primaryBlogAddresses.of(authors.stream().map(Member::getId).toList(), viewerId);
        Map<Long, String> photos = profileImages.thumbnailUrls(
                authors.stream().map(Member::getProfileImageId).toList());
        Map<Long, List<CommentView>> repliesByParent = replies.stream().collect(Collectors.groupingBy(
                reply -> reply.getParent().getId(), LinkedHashMap::new,
                Collectors.mapping(reply -> view(reply, blog, viewerId, addresses.get(reply.getMember().getId()),
                        photos.get(reply.getMember().getProfileImageId())),
                        Collectors.toList())));
        List<CommentView> views = parents.stream()
                .map(parent -> view(parent, blog, viewerId, addresses.get(parent.getMember().getId()),
                        photos.get(parent.getMember().getProfileImageId()))
                        .withReplies(repliesByParent.getOrDefault(parent.getId(), List.of())))
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
    public CommentView write(Blog blog, Long postId, LoginMember member, String content, Long parentId) {
        Post post = writablePost(blog, postId, member);
        // 댓글 INSERT(외래 키 공유 잠금) 뒤 댓글 수 UPDATE(배타 잠금)가 동시에 엇갈리면 데드락이라 글 행부터 잠근다
        postRepository.lockById(post.getId());
        Member author = memberRepository.getReferenceById(member.id());
        Comment comment = commentRepository.save(parentId == null
                ? Comment.write(post, author, content.trim(), false)
                : Comment.reply(parentOf(post, parentId), author, content.trim(), false));
        postRepository.addCommentCount(post.getId(), 1);
        Comment saved = commentRepository.findBy(
                (root, query, cb) -> cb.equal(root.get("id"), comment.getId()),
                query -> query.project("member").first()).orElseThrow();
        return view(saved, blog, member.id(), primaryBlogAddresses.of(List.of(member.id()), member.id())
                .get(member.id()), profileImages.thumbnailUrl(saved.getMember().getProfileImageId()));
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
        postRepository.lockById(comment.getPost().getId());
        comment.delete(LocalDateTime.now(clock));
        // 댓글 수 UPDATE가 영속성 컨텍스트를 비우므로(clearAutomatically) 삭제를 먼저 DB에 보낸다
        commentRepository.flush();
        postRepository.addCommentCount(comment.getPost().getId(), -1);
    }

    private CommentView view(Comment comment, Blog blog, Long viewerId, String authorAddress, String authorPhoto) {
        boolean author = comment.isWrittenBy(viewerId);
        boolean blogOwner = blog.isOwnedBy(viewerId);
        if (comment.isDeleted()) {
            // 답글이 남아 자리만 있는 부모. 누구에게나 내용·작성자 없이, 다시 지울 것도 없다
            return new CommentView(comment, CommentView.State.DELETED, null, null, false, null, List.of());
        }
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
        return new CommentView(comment, state, authorAddress, authorPhoto, author || blogOwner, blind, List.of());
    }

    private Map<String, String> blindReason(Comment comment) {
        return moderationLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
                        ModerationTargetType.COMMENT, comment.getId(), ModerationAction.BLIND)
                .map(log -> Map.of("reason", log.getReason().name(), "reasonMessage", log.getReason().getMessage()))
                .orElse(Map.of());
    }

}
