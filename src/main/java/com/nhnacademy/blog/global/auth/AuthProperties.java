package com.nhnacademy.blog.global.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 토큰 설정 (R-03). jwtSecret은 HS256 서명 키로 32바이트 이상이어야 한다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl,
                             boolean cookieSecure) {
}
