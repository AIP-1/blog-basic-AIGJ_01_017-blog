package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그아웃 (T020, AUTH-02, spec US1 수용 시나리오 9).
 */
class LogoutIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Test
    void logoutClearsCookiesForAllBlogAddresses() throws Exception {
        Cookie[] cookies = testMembers.loginCookies(testMembers.create(), true);

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.HOST, "alpha.blog.test")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookies))
                .andExpect(status().isNoContent())
                .andReturn();

        // .blog.test 도메인 쿠키를 지우므로 플랫폼과 모든 블로그 주소가 함께 로그아웃된다
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2)
                .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0", "Domain=.blog.test"));
    }

    @Test
    void oldCookiesNoLongerWorkAfterLogout() throws Exception {
        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member, true);

        mockMvc.perform(post("/api/auth/logout").header("X-Requested-With", "XMLHttpRequest").cookie(cookies))
                .andExpect(status().isNoContent());

        // 쿠키를 지우지 않고 남겨 둔 브라우저가 다시 보내도 Access·Refresh 둘 다 무효다
        mockMvc.perform(get("/api/me").header(HttpHeaders.HOST, "beta.blog.test").cookie(cookies))
                .andExpect(status().isUnauthorized());
        Cookie[] refreshOnly = Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals("refresh_token"))
                .toArray(Cookie[]::new);
        mockMvc.perform(get("/api/me").cookie(refreshOnly)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousLogoutIs401() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

}
