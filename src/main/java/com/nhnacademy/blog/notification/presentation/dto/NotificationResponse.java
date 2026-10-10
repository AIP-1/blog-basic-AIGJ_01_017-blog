package com.nhnacademy.blog.notification.presentation.dto;

import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.notification.domain.Notification;
import java.time.OffsetDateTime;

/**
 * 알림 하나 `{ id, type, message, link, read, createdAt }` (contracts/rest-api.md SUB-04).
 * link는 눌렀을 때 갈 화면의 전체 주소다(블로그 주소는 서브도메인이 달라 전체 주소가 필요하다).
 */
public record NotificationResponse(Long id, String type, String message, String link, boolean read,
                                   OffsetDateTime createdAt) {

    public static NotificationResponse of(Notification notification, String link) {
        return new NotificationResponse(notification.getId(), notification.getType().name(), notification.getMessage(),
                link, notification.isRead(), DateTimes.toOffset(notification.getCreatedAt()));
    }

}
