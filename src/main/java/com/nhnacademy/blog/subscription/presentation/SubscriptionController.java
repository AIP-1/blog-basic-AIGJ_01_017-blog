package com.nhnacademy.blog.subscription.presentation;

import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.subscription.application.SubscriptionService;
import com.nhnacademy.blog.subscription.presentation.dto.SubscriptionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구독·해제 (T089, SUB-01, SUB-03). 블로그 번호로 가리키므로 어느 주소(플랫폼·블로그)에서 불러도 같다.
 * PUT·DELETE라 같은 요청을 두 번 보내도 결과가 같다(연타 방지 키가 필요 없음).
 */
@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** 회원만(비회원 401). 볼 수 없는 블로그 404, 자기 블로그 400. */
    @PutMapping("/api/blogs/{blogId}/subscription")
    @PreAuthorize("isAuthenticated()")
    public SubscriptionResponse subscribe(@PathVariable Long blogId, @AuthenticationPrincipal LoginMember member) {
        return SubscriptionResponse.from(subscriptionService.subscribe(blogId, member));
    }

    @DeleteMapping("/api/blogs/{blogId}/subscription")
    @PreAuthorize("isAuthenticated()")
    public SubscriptionResponse unsubscribe(@PathVariable Long blogId, @AuthenticationPrincipal LoginMember member) {
        return SubscriptionResponse.from(subscriptionService.unsubscribe(blogId, member));
    }

}
