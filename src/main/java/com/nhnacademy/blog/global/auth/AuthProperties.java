package com.nhnacademy.blog.global.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 토큰 설정 (R-03). jwtSecret은 HS256 서명 키로 32바이트 이상이어야 한다.
 * refreshTokenTtl은 로그인 유지를 골랐을 때의 기한, idleTimeout은 고르지 않았을 때 무활동으로 로그아웃되는 시간이다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl,
                             Duration idleTimeout, boolean cookieSecure) {
}
