package com.nhnacademy.blog.notification.domain;

/** 알림 종류 (SUB-04, ERD ck_notification_type). SANCTION(제재·해제)은 관리자 조치(스텝 18)가 만든다. */
public enum NotificationType {
    COMMENT,
    REPLY,
    LIKE,
    SUBSCRIBE,
    SANCTION
}
