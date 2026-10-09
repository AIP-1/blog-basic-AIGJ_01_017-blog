package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.global.auth.AuthCookieManager;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 로그인 유지 (T062, AUTH-03): 브라우저를 닫았다 열기와 Access 쿠키 재발급 API.
 * 브라우저를 닫으면 Max-Age가 없는 쿠키(세션 쿠키)는 사라지고 Max-Age가 있는 쿠키만 남는다. 그 상태를 흉내 낸다.
 */
class KeepLoggedInIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "password123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void rememberMeSurvivesBrowserRestart() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);
        Cookie[] afterRestart = cookiesSurvivingRestart(login(member, true));

        assertThat(afterRestart).extracting(Cookie::getName).containsExactly(AuthCookieManager.REFRESH_COOKIE);
        mockMvc.perform(get("/api/me").cookie(afterRestart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()));
    }

    @Test
    void withoutRememberMeBrowserRestartLogsOut() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);

        assertThat(cookiesSurvivingRestart(login(member, false))).isEmpty();
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshApiRenewsAccessCookieFromRefreshCookie() throws Exception {
        Cookie refreshOnly = refreshCookie(testMembers.create());

        MvcResult result = refresh(refreshOnly).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .filteredOn(cookie -> cookie.startsWith(AuthCookieManager.ACCESS_COOKIE + "="))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie).doesNotContain("Max-Age"));
    }

    @Test
    void refreshApiNeedsLiveRefreshToken() throws Exception {
        refresh().andExpect(status().isUnauthorized());

        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member, true);
        mockMvc.perform(post("/api/auth/logout").header("X-Requested-With", "XMLHttpRequest").cookie(cookies))
                .andExpect(status().isNoContent());
        refresh(cookie(cookies, AuthCookieManager.REFRESH_COOKIE)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshApiLogsOutWithdrawnAndBlocksSuspendedMembers() throws Exception {
        Member withdrawn = testMembers.create();
        Cookie withdrawnRefresh = refreshCookie(withdrawn);
        jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", withdrawn.getId());
        refresh(withdrawnRefresh).andExpect(status().isUnauthorized());

        Member suspended = testMembers.create();
        Cookie suspendedRefresh = refreshCookie(suspended);
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED', suspended_until = '2099-01-01 00:00:00' "
                + "WHERE id = ?", suspended.getId());
        refresh(suspendedRefresh)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"));
    }

    private MvcResult login(Member member, boolean rememberMe) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","rememberMe":%s}
                                """.formatted(member.getEmail(), PASSWORD, rememberMe)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** 응답의 Set-Cookie 가운데 브라우저를 닫아도 남는 것(Max-Age가 있는 것)만 다음 요청에 보낸다. */
    private Cookie[] cookiesSurvivingRestart(MvcResult login) {
        List<String> setCookies = login.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return setCookies.stream()
                .filter(header -> header.contains("Max-Age="))
                .map(header -> HttpCookie.parse(header).getFirst())
                .map(parsed -> new Cookie(parsed.getName(), parsed.getValue()))
                .toArray(Cookie[]::new);
    }

    private Cookie refreshCookie(Member member) {
        return cookie(testMembers.loginCookies(member, true), AuthCookieManager.REFRESH_COOKIE);
    }

    private ResultActions refresh(Cookie... cookies) throws Exception {
        var request = post("/api/auth/token/refresh").header("X-Requested-With", "XMLHttpRequest");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

    private Cookie cookie(Cookie[] cookies, String name) {
        return Arrays.stream(cookies).filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

}
