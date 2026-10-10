package com.nhnacademy.blog.notification.application;

import com.nhnacademy.blog.global.web.TimeIdCursor;
import java.util.List;

/**
 * 알림 한 묶음. 볼 수 없게 된 대상의 알림은 빠져서 20개보다 적을 수 있다.
 * next는 읽은 행 기준의 다음 커서다(빠진 알림 다음부터 이어 읽는다). 끝이면 null.
 */
public record NotificationPage(List<NotificationView> items, TimeIdCursor next) {
}
