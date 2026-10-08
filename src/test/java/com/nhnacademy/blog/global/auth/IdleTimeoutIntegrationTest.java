package com.nhnacademy.blog.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 유지를 고르지 않으면 30분 동안 아무 요청이 없을 때 로그아웃된다 (AUTH-03, R-03).
 */
class IdleTimeoutIntegrationTest extends IntegrationTestSupport {

    private static final long THIRTY_MINUTES = Duration.ofMinutes(30).toSeconds();
    private static final long FOURTEEN_DAYS = Duration.ofDays(14).toSeconds();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    JwtTokenProvider tokenProvider;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void withoutRememberMeRefreshLivesForIdleTimeout() {
        Cookie[] cookies = testMembers.loginCookies(testMembers.create(), false);

        assertThat(ttlSeconds(cookies)).isBetween(THIRTY_MINUTES - 5, THIRTY_MINUTES);
    }

    @Test
    void withRememberMeRefreshLivesFourteenDays() {
        Cookie[] cookies = testMembers.loginCookies(testMembers.create(), true);

        assertThat(ttlSeconds(cookies)).isBetween(FOURTEEN_DAYS - 5, FOURTEEN_DAYS);
    }

    @Test
    void activityExtendsIdleTimeout() throws Exception {
        Cookie[] cookies = testMembers.loginCookies(testMembers.create(), false);
        redis.expire(refreshKey(cookies), Duration.ofSeconds(60));

        mockMvc.perform(get("/api/test/me").cookie(cookies)).andExpect(status().isOk());

        assertThat(ttlSeconds(cookies)).isBetween(THIRTY_MINUTES - 5, THIRTY_MINUTES);
    }

    @Test
    void activityDoesNotShortenRememberMe() throws Exception {
        Cookie[] cookies = testMembers.loginCookies(testMembers.create(), true);

        mockMvc.perform(get("/api/test/me").cookie(cookies)).andExpect(status().isOk());

        assertThat(ttlSeconds(cookies)).isGreaterThan(THIRTY_MINUTES);
    }

    @Test
    void idleTooLongIsLoggedOutOnceAccessTokenExpires() throws Exception {
        Cookie[] cookies = testMembers.loginCookies(testMembers.create(), false);
        // 30분 동안 요청이 없어 Redis에서 사라진 상태. Access 토큰(30분)도 이미 끝났다고 보고 Refresh 쿠키만 보낸다
        redis.delete(refreshKey(cookies));

        mockMvc.perform(get("/api/test/me").cookie(cookie(cookies, AuthCookieManager.REFRESH_COOKIE)))
                .andExpect(status().isUnauthorized());
    }

    private long ttlSeconds(Cookie[] cookies) {
        return redis.getExpire(refreshKey(cookies), TimeUnit.SECONDS);
    }

    private String refreshKey(Cookie[] cookies) {
        String token = cookie(cookies, AuthCookieManager.REFRESH_COOKIE).getValue();
        return "auth:refresh:" + tokenProvider.parse(token, TokenType.REFRESH).orElseThrow().id();
    }

    private Cookie cookie(Cookie[] cookies, String name) {
        return Arrays.stream(cookies).filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

}
