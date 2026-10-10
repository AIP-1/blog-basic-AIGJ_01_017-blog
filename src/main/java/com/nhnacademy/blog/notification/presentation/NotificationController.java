package com.nhnacademy.blog.notification.presentation;

import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.BlogHostResolver;
import com.nhnacademy.blog.global.web.CursorResponse;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.notification.application.NotificationPage;
import com.nhnacademy.blog.notification.application.NotificationService;
import com.nhnacademy.blog.notification.application.NotificationView;
import com.nhnacademy.blog.notification.presentation.dto.NotificationResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 알림 (T113, SUB-04). 모두 회원만(비회원 401).
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final BlogHostResolver blogHostResolver;

    public NotificationController(NotificationService notificationService, BlogHostResolver blogHostResolver) {
        this.notificationService = notificationService;
        this.blogHostResolver = blogHostResolver;
    }

    /** 최신순 20개씩 더보기. 대상이 지워졌거나 볼 수 없게 된 알림은 빠진다. */
    @GetMapping("/api/me/notifications")
    public CursorResponse<NotificationResponse> list(@AuthenticationPrincipal LoginMember member,
                                                     @RequestParam(required = false) String cursor,
                                                     HttpServletRequest request) {
        NotificationPage page = notificationService.list(member.id(), TimeIdCursor.decode(cursor));
        return new CursorResponse<>(page.items().stream()
                .map(view -> NotificationResponse.of(view.notification(), link(request, view)))
                .toList(), page.next() == null ? null : page.next().encode());
    }

    /** 머리글 종 표시. 어느 주소에서 불러도 같다. */
    @GetMapping("/api/me/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal LoginMember member) {
        return Map.of("count", notificationService.unreadCount(member.id()));
    }

    @PutMapping("/api/me/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal LoginMember member, @PathVariable Long id) {
        notificationService.read(member.id(), id);
    }

    @PutMapping("/api/me/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal LoginMember member) {
        notificationService.readAll(member.id());
    }

    private String link(HttpServletRequest request, NotificationView view) {
        return view.blog() == null
                ? blogHostResolver.platformUrl(request, view.path())
                : blogHostResolver.blogUrl(request, view.blog(), view.path());
    }

}
