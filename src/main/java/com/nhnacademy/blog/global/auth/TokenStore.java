package com.nhnacademy.blog.global.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis에 토큰 상태를 둔다 (R-03, AUTH-02).
 * <ul>
 *   <li>Refresh 토큰은 살아 있는 것만 저장한다. 로그아웃하면 지운다.</li>
 *   <li>로그인 유지를 고르지 않은 Refresh 토큰은 무활동 시간만큼만 살고, 요청이 올 때마다 연장한다.</li>
 *   <li>로그아웃한 Access 토큰은 만료 때까지 막아 둔다.</li>
 * </ul>
 */
@Component
public class TokenStore {

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String BLOCKED_ACCESS_PREFIX = "auth:blocked-access:";

    private final StringRedisTemplate redis;
    private final Clock clock;

    public TokenStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /** idleTimeout이 null이면 토큰 만료 때까지(로그인 유지), 아니면 무활동 시간만큼 둔다. */
    public void saveRefresh(IssuedToken refreshToken, Long memberId, Duration idleTimeout) {
        Duration ttl = untilExpiry(refreshToken.expiresAt());
        if (idleTimeout != null && idleTimeout.compareTo(ttl) < 0) {
            ttl = idleTimeout;
        }
        if (!ttl.isZero()) {
            redis.opsForValue().set(REFRESH_PREFIX + refreshToken.id(), String.valueOf(memberId), ttl);
        }
    }

    /** 활동이 있었으니 무활동 기한을 다시 센다. 이미 끝났으면 false. */
    public boolean extendRefresh(String refreshTokenId, Duration idleTimeout) {
        return Boolean.TRUE.equals(redis.expire(REFRESH_PREFIX + refreshTokenId, idleTimeout));
    }

    public boolean isRefreshActive(String refreshTokenId) {
        return Boolean.TRUE.equals(redis.hasKey(REFRESH_PREFIX + refreshTokenId));
    }

    public void deleteRefresh(String refreshTokenId) {
        redis.delete(REFRESH_PREFIX + refreshTokenId);
    }

    public void blockAccess(String accessTokenId, Instant expiresAt) {
        Duration ttl = untilExpiry(expiresAt);
        if (!ttl.isZero()) {
            redis.opsForValue().set(BLOCKED_ACCESS_PREFIX + accessTokenId, "1", ttl);
        }
    }

    public boolean isAccessBlocked(String accessTokenId) {
        return Boolean.TRUE.equals(redis.hasKey(BLOCKED_ACCESS_PREFIX + accessTokenId));
    }

    private Duration untilExpiry(Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }

}
