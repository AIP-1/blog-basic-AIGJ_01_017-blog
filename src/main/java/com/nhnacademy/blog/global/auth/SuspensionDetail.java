package com.nhnacademy.blog.global.auth;

import java.time.OffsetDateTime;

/**
 * 403 MEMBER_SUSPENDED의 detail. suspendedUntil이 null이면 영구 정지다.
 */
public record SuspensionDetail(String reason, String reasonMessage, OffsetDateTime suspendedUntil) {
}
