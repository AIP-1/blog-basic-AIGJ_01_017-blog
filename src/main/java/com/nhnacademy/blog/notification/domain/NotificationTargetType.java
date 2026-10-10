package com.nhnacademy.blog.notification.domain;

/** 알림이 가리키는 것 (ERD ck_notification_target_type). 누르면 갈 화면과 "아직 볼 수 있나"를 이것으로 정한다. */
public enum NotificationTargetType {
    POST,
    COMMENT,
    BLOG,
    MEMBER
}
