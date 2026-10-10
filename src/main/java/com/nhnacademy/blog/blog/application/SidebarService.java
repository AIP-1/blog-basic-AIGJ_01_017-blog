package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogVisitCounter;
import com.nhnacademy.blog.blog.domain.SidebarModuleType;
import com.nhnacademy.blog.category.application.CategoryTreeService;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.image.application.ProfileImages;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import com.nhnacademy.blog.tag.application.TagListService;
import jakarta.persistence.criteria.Join;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이드바 (T025 BLOG-04, T086 BLOG-05). 볼 수 없는 글과 그 글의 댓글은 빠진다(헌법 원칙 II).
 * 주인이 정한 순서로 보이는 모듈만 내려 주고, 숨긴 모듈의 데이터는 읽지 않는다.
 */
@Service
public class SidebarService {

    public static final int RECENT_SIZE = 5;

    private static final Sort LATEST_POSTS = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private static final Sort LATEST_COMMENTS = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort MOST_VIEWED = Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("id"));

    private final CategoryTreeService categoryTreeService;
    private final TagListService tagListService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ProfileImages profileImages;
    private final SidebarModules sidebarModules;
    private final BlogVisitCounter blogVisitCounter;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public SidebarService(CategoryTreeService categoryTreeService, TagListService tagListService,
                          PostRepository postRepository, CommentRepository commentRepository,
                          ProfileImages profileImages, SidebarModules sidebarModules, BlogVisitCounter blogVisitCounter,
                          SubscriptionRepository subscriptionRepository, Clock clock) {
        this.sidebarModules = sidebarModules;
        this.blogVisitCounter = blogVisitCounter;
        this.subscriptionRepository = subscriptionRepository;
        this.categoryTreeService = categoryTreeService;
        this.tagListService = tagListService;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.profileImages = profileImages;
        this.clock = clock;
    }

    /** 댓글 작성자 닉네임처럼 지연 로딩되는 값은 이 트랜잭션 안에서 꺼내 기록(record)으로 넘긴다. */
    @Transactional(readOnly = true)
    public Sidebar sidebar(Blog blog, Long viewerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SidebarModuleType> shown = sidebarModules.of(blog.getId()).stream()
                .filter(SidebarModules.Slot::visible)
                .map(SidebarModules.Slot::type)
                .toList();
        return new Sidebar(blog, shown, profileImages.thumbnailUrl(blog.getProfileImageId()),
                shown.contains(SidebarModuleType.CATEGORY) ? categoryTreeService.tree(blog, viewerId) : null,
                shown.contains(SidebarModuleType.TAG) ? tagListService.tags(blog, viewerId) : null,
                shown.contains(SidebarModuleType.RECENT_POST) ? recentPosts(blog, viewerId, now) : null,
                shown.contains(SidebarModuleType.RECENT_COMMENT) ? recentComments(blog, viewerId, now) : null,
                shown.contains(SidebarModuleType.VISITOR) ? visitor(blog, now.toLocalDate()) : null,
                shown.contains(SidebarModuleType.POPULAR_POST) ? popularPosts(blog, viewerId, now) : null,
                shown.contains(SidebarModuleType.SUBSCRIBE) ? subscribe(blog, viewerId) : null);
    }

    /** 오늘은 blog_visit, 어제는 모은 값(없으면 blog_visit), 누적은 어제까지의 누적 + 오늘. */
    private Sidebar.Visitor visitor(Blog blog, LocalDate today) {
        long todayCount = blogVisitCounter.visitors(blog.getId(), today);
        return new Sidebar.Visitor(todayCount, blogVisitCounter.visitorsOf(blog.getId(), today.minusDays(1)),
                blog.getTotalVisitorCount() + todayCount);
    }

    /** 누적 조회수 상위 5개, 같으면 나중 글. 블로그 목록과 같은 조건(listedIn)이라 볼 수 없는 글은 빠진다. */
    private List<Sidebar.PopularPost> popularPosts(Blog blog, Long viewerId, LocalDateTime now) {
        return postRepository.findBy(PostSpecifications.listedIn(blog, viewerId, now),
                        query -> query.sortBy(MOST_VIEWED).limit(RECENT_SIZE).all())
                .stream()
                .map(post -> new Sidebar.PopularPost(post.getId(), post.getTitle(), post.getViewCount()))
                .toList();
    }

    private Sidebar.Subscribe subscribe(Blog blog, Long viewerId) {
        boolean subscribed = viewerId != null && subscriptionRepository.existsByMemberIdAndBlogId(viewerId, blog.getId());
        return new Sidebar.Subscribe(blog.getId(), subscriptionRepository.countByBlogId(blog.getId()), subscribed);
    }

    private List<Sidebar.RecentPost> recentPosts(Blog blog, Long viewerId, LocalDateTime now) {
        return postRepository.findBy(PostSpecifications.listedIn(blog, viewerId, now),
                        query -> query.sortBy(LATEST_POSTS).limit(RECENT_SIZE).all())
                .stream()
                .map(post -> new Sidebar.RecentPost(post.getId(), post.getTitle()))
                .toList();
    }

    private List<Sidebar.RecentComment> recentComments(Blog blog, Long viewerId, LocalDateTime now) {
        Specification<Comment> condition = (root, query, cb) -> {
            Join<Comment, Post> post = root.join("post");
            return cb.and(cb.isNull(root.get("deletedAt")),
                    PostSpecifications.listedIn(post, query, cb, blog, viewerId, now));
        };
        return commentRepository.findBy(condition, query -> query.sortBy(LATEST_COMMENTS).limit(RECENT_SIZE).all())
                .stream()
                .map(SidebarService::toRecentComment)
                .toList();
    }

    private static Sidebar.RecentComment toRecentComment(Comment comment) {
        Sidebar.CommentState state = comment.isBlinded() ? Sidebar.CommentState.BLINDED
                : comment.isSecret() ? Sidebar.CommentState.SECRET
                : Sidebar.CommentState.NORMAL;
        boolean shown = state == Sidebar.CommentState.NORMAL;
        return new Sidebar.RecentComment(comment.getId(), comment.getPost().getId(),
                shown ? comment.getContent() : null, shown ? comment.getMember().getNickname() : null, state);
    }

}
