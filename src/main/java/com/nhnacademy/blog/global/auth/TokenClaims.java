package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.member.domain.Role;
import java.time.Instant;

/**
 * 검증을 통과한 토큰의 내용. role은 Access 토큰에만 있다.
 */
public record TokenClaims(TokenType type, Long memberId, Role role, String id, Instant expiresAt,
                          boolean rememberMe) {
}
