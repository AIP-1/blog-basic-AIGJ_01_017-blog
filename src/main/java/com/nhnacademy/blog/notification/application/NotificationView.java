package com.nhnacademy.blog.notification.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.notification.domain.Notification;

/**
 * 받는 사람이 아직 볼 수 있는 알림 하나와 누르면 갈 곳.
 *
 * @param blog 갈 화면이 있는 블로그. 플랫폼 화면(마이페이지 등)이면 null
 * @param path 그 주소 안의 경로와 조각(#comment-88)
 */
public record NotificationView(Notification notification, Blog blog, String path) {
}
