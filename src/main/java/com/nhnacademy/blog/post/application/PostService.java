package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.security.HtmlSanitizer;
import com.nhnacademy.blog.global.security.SummaryExtractor;
import com.nhnacademy.blog.global.visibility.PostAccess;
import com.nhnacademy.blog.global.visibility.PostVisibilityPolicy;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostBody;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.tag.application.TagService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 발행·수정·삭제·공개 범위 변경 (T031~T034, POST-01·02·03·06).
 * 본문은 저장하기 전에 서버가 허용 목록으로 정화하고, 요약은 정화된 본문에서 만든다(R-05).
 * 화면을 거치지 않은 요청도 같은 길을 지난다.
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final CommentRepository commentRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final PostVisibilityPolicy postVisibilityPolicy;
    private final HtmlSanitizer htmlSanitizer;
    private final SummaryExtractor summaryExtractor;
    private final TagService tagService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PostService(PostRepository postRepository, CategoryRepository categoryRepository,
                       CommentRepository commentRepository, ModerationLogRepository moderationLogRepository,
                       PostVisibilityPolicy postVisibilityPolicy,
                       HtmlSanitizer htmlSanitizer, SummaryExtractor summaryExtractor, TagService tagService,
                       ApplicationEventPublisher events, Clock clock) {
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.postVisibilityPolicy = postVisibilityPolicy;
        this.htmlSanitizer = htmlSanitizer;
        this.summaryExtractor = summaryExtractor;
        this.tagService = tagService;
        this.events = events;
        this.clock = clock;
    }

    /** 발행. 지금이 처음 발행 시각이고, 글 번호(id)가 곧 글 주소다. 주인 검사는 컨트롤러가 했다. */
    @Transactional
    public Post publish(Blog blog, PostCommand command) {
        Post post = Post.published(blog, category(blog, command.categoryId()), command.title().trim(),
                body(command.contentHtml()), command.visibility(), command.topic(), LocalDateTime.now(clock));
        post.replaceTags(tagService.resolve(blog, command.tagNames()));
        Post saved = postRepository.save(post);
        // 받는 쪽(추천 임베딩)은 이 트랜잭션이 커밋된 뒤에 움직인다(@TransactionalEventListener)
        events.publishEvent(new PostContentChangedEvent(saved.getId()));
        return saved;
    }

    /**
     * 주인만 다루는 글을 찾는다. 상태 코드 순서(contracts/rest-api.md)를 따른다.
     * 없거나 이 블로그 글이 아니거나 볼 수 없으면 404, 볼 수는 있는데 비회원이면 401, 회원이면 403.
     */
    @Transactional(readOnly = true)
    public Post findOwned(Blog blog, Long postId, LoginMember member) {
        Long viewerId = member == null ? null : member.id();
        return switch (postVisibilityPolicy.decide(postId, blog, viewerId)) {
            case PostAccess.Owner owner -> owner.post();
            case PostAccess.NotFound notFound -> throw new BusinessException(ErrorCode.NOT_FOUND);
            // 블로그 주소 API에서 다른 블로그의 글 번호는 없는 글이다 (301은 화면 주소 단계에서만)
            case PostAccess.MovedTo movedTo -> throw new BusinessException(ErrorCode.NOT_FOUND);
            case PostAccess.Visible visible -> throw notOwner(member);
            case PostAccess.SubscribersOnly subscribersOnly -> throw notOwner(member);
        };
    }

    /** 주인이 고칠 수 있는 글. findOwned에 더해, 관리자가 숨긴 글이면 403 POST_BLINDED(사유와 함께)다. */
    @Transactional(readOnly = true)
    public Post findEditable(Blog blog, Long postId, LoginMember member) {
        Post post = findOwned(blog, postId, member);
        if (post.isBlinded()) {
            throw new BusinessException(ErrorCode.POST_BLINDED, blindReason(post));
        }
        return post;
    }

    /**
     * 수정 (POST-02). 숨긴 글은 고칠 수 없다.
     * findEditable이 이 트랜잭션 안에서 읽은 글이라, 바꾼 값은 트랜잭션이 끝날 때 저장된다(변경 감지).
     */
    @Transactional
    public Post edit(Blog blog, Long postId, LoginMember member, PostCommand command) {
        Post post = findEditable(blog, postId, member);
        post.edit(category(blog, command.categoryId()), command.title().trim(), body(command.contentHtml()),
                command.visibility(), command.topic());
        post.replaceTags(tagService.resolve(blog, command.tagNames()));
        events.publishEvent(new PostContentChangedEvent(post.getId()));
        return post;
    }

    /** 편집용 글. 태그 이름과 숨김 사유를 트랜잭션 안에서 꺼내 둔다. */
    @Transactional(readOnly = true)
    public ManagedPost managed(Blog blog, Long postId, LoginMember member) {
        Post post = findOwned(blog, postId, member);
        return new ManagedPost(post, post.tagNames(), post.isBlinded() ? blindReason(post) : null);
    }

    /** 공개 범위만 바꾼다 (POST-06). */
    @Transactional
    public void changeVisibility(Blog blog, Long postId, LoginMember member, Visibility visibility) {
        findOwned(blog, postId, member).changeVisibility(visibility);
    }

    /**
     * 삭제 (POST-03). 글은 소프트 삭제, 그 글의 댓글도 소프트 삭제, 공감과 알림은 지운다. 모두 한 트랜잭션이다.
     * 글이 지워지면 목록·글 수에서 바로 빠진다(가시성 조건이 deleted_at을 본다). 숨긴 글도 지울 수 있다.
     */
    @Transactional
    public void delete(Blog blog, Long postId, LoginMember member) {
        Long id = findOwned(blog, postId, member).getId();
        LocalDateTime now = LocalDateTime.now(clock);
        postRepository.deleteNotifications(id);
        postRepository.deleteLikes(id);
        // 댓글 일괄 수정이 영속성 컨텍스트를 비우므로(clearAutomatically) 글은 그 뒤에 다시 읽어 지운다
        commentRepository.softDeleteByPostId(id, now);
        postRepository.findById(id).orElseThrow().delete(now);
        events.publishEvent(new PostDeletedEvent(id));
    }

    /** 숨김 사유는 글 행이 아니라 moderation_log의 최신 BLIND 행에 있다 (ADMIN-03). */
    public Map<String, String> blindReason(Post post) {
        return moderationLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
                        ModerationTargetType.POST, post.getId(), ModerationAction.BLIND)
                .map(log -> Map.of("reason", log.getReason().name(), "reasonMessage", log.getReason().getMessage()))
                .orElse(Map.of());
    }

    /** 볼 수는 있지만 주인이 아니다: 비회원 401, 회원 403 (BlogOwnerGuard와 같은 순서). */
    private static BusinessException notOwner(LoginMember member) {
        return new BusinessException(member == null ? ErrorCode.UNAUTHORIZED : ErrorCode.FORBIDDEN);
    }

    /** 받은 HTML을 정화하고, 정화된 본문에서 검색용 글자와 요약을 만든다. */
    private PostBody body(String rawHtml) {
        String html = htmlSanitizer.sanitize(rawHtml);
        return new PostBody(html, summaryExtractor.plainText(html), summaryExtractor.extract(html));
    }

    /** null이면 미분류. 다른 블로그의 카테고리 번호면 400. */
    private Category category(Blog blog, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .filter(category -> category.belongsTo(blog))
                .orElseThrow(() -> BusinessException.invalidField("categoryId", "카테고리를 찾을 수 없습니다."));
    }

}
