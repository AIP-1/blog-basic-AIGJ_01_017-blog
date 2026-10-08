package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.blog.domain.Blog;
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

    private final PostRepository postRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final PrimaryBlogAddresses primaryBlogAddresses;
    private final Clock clock;

    public BlogQueryService(PostRepository postRepository, SubscriptionRepository subscriptionRepository,
                            ModerationLogRepository moderationLogRepository,
                            PrimaryBlogAddresses primaryBlogAddresses, Clock clock) {
        this.postRepository = postRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.primaryBlogAddresses = primaryBlogAddresses;
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
        return new BlogDetail(blog, primaryBlogAddresses.ofOwner(blog, viewerId), postCount, subscriberCount, owner,
                subscribed, owner ? restriction(blog) : null);
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
