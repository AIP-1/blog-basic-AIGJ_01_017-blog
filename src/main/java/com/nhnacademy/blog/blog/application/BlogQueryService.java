package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 정보 조회 (T023, BLOG-03). 글 수는 보는 사람이 볼 수 있는 글만 센다(헌법 원칙 II).
 */
@Service
public class BlogQueryService {

    private final BlogRepository blogRepository;
    private final PostRepository postRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final BlogVisibilityPolicy blogVisibilityPolicy;
    private final Clock clock;

    public BlogQueryService(BlogRepository blogRepository, PostRepository postRepository,
                            SubscriptionRepository subscriptionRepository,
                            ModerationLogRepository moderationLogRepository,
                            BlogVisibilityPolicy blogVisibilityPolicy, Clock clock) {
        this.blogRepository = blogRepository;
        this.postRepository = postRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
        this.clock = clock;
    }

    /** blog는 주인(member)이 읽혀 있어야 한다(@CurrentBlog, BlogRepository.findByAddress). */
    @Transactional(readOnly = true)
    public BlogDetail detail(Blog blog, Long viewerId) {
        boolean owner = blog.isOwnedBy(viewerId);
        long postCount = postRepository.count(
                PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock)));
        long subscriberCount = subscriptionRepository.countByBlogId(blog.getId());
        boolean subscribed = viewerId != null
                && subscriptionRepository.existsByMemberIdAndBlogId(viewerId, blog.getId());
        return new BlogDetail(blog, ownerPrimaryBlogAddress(blog, viewerId), postCount, subscriberCount, owner,
                subscribed, owner ? restriction(blog) : null);
    }

    private String ownerPrimaryBlogAddress(Blog blog, Long viewerId) {
        if (blog.isPrimary()) {
            return blog.getAddress();
        }
        return blogRepository.findPrimaryByMemberId(blog.getMember().getId())
                .filter(primary -> blogVisibilityPolicy.canView(primary, viewerId))
                .map(Blog::getAddress)
                .orElse(null);
    }

    /** 이용 제한 사유는 블로그 행이 아니라 moderation_log의 최신 RESTRICT_BLOG 행에 있다 (ADMIN-05). */
    private BlogDetail.Restriction restriction(Blog blog) {
        if (!blog.isRestricted()) {
            return null;
        }
        return moderationLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
                        ModerationTargetType.BLOG, blog.getId(), ModerationAction.RESTRICT_BLOG)
                .map(log -> new BlogDetail.Restriction(log.getReason().name(), log.getReason().getMessage()))
                .orElse(new BlogDetail.Restriction(null, null));
    }

}
