package com.nhnacademy.blog.subscription.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.notification.application.NotificationService;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 구독·해제 (T089, SUB-01, SUB-03). 연달아 눌러도 한 번으로 센다(구독은 넣거나 그대로, 해제는 지우거나 그대로).
 * 블로그를 볼 수 없으면(없음, 지움, 이용 제한, 주인 정지, 이사) 404, 자기 블로그는 400.
 * 그 블로그에서 차단된 회원(MNG-04)은 백로그(T084)에서 403 BLOCKED_BY_BLOG를 더한다.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BlogRepository blogRepository;
    private final MemberRepository memberRepository;
    private final BlogVisibilityPolicy blogVisibilityPolicy;
    private final NotificationService notificationService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, BlogRepository blogRepository,
                               MemberRepository memberRepository, BlogVisibilityPolicy blogVisibilityPolicy,
                               NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
        this.notificationService = notificationService;
    }

    @Transactional
    public SubscriptionResult subscribe(Long blogId, LoginMember member) {
        Blog blog = subscribable(blogId, member);
        if (blog.isOwnedBy(member.id())) {
            throw BusinessException.invalidField("blogId", "자기 블로그는 구독할 수 없습니다.");
        }
        if (subscriptionRepository.insertIfAbsent(member.id(), blog.getId()) == 1) {
            // 새로 구독했을 때만 주인에게 알린다(연타한 두 번째 요청은 알리지 않음, SUB-04)
            notificationService.subscribed(blog, memberRepository.getReferenceById(member.id()));
        }
        return new SubscriptionResult(true, subscriptionRepository.countByBlogId(blog.getId()));
    }

    /** 해제는 구독하지 않았어도 같은 응답이다(멱등). 자기 블로그는 구독이 없으니 그대로 false. */
    @Transactional
    public SubscriptionResult unsubscribe(Long blogId, LoginMember member) {
        Blog blog = subscribable(blogId, member);
        subscriptionRepository.deleteIfPresent(member.id(), blog.getId());
        return new SubscriptionResult(false, subscriptionRepository.countByBlogId(blog.getId()));
    }

    /** 이사한 블로그는 새 블로그를 구독하게 한다(옛 블로그는 301되는 자리라 404). */
    private Blog subscribable(Long blogId, LoginMember member) {
        Long viewerId = member == null ? null : member.id();
        return blogRepository.findWithMemberById(blogId)
                .filter(blog -> blogVisibilityPolicy.canView(blog, viewerId) && !blog.isMoved())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

}
