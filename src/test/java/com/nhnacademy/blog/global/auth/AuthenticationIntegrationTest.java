package com.nhnacademy.blog.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그인 쿠키, 관리자 영역, CSRF 헤더, 정지·탈퇴 회원 차단 (T007, T008).
 */
class AuthenticationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    AuthCookieManager cookieManager;

    @Autowired
    ModerationLogRepository moderationLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void anonymousGets401OnLoginRequiredApi() throws Exception {
        mockMvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void loginCookieAuthenticates() throws Exception {
        Member member = testMembers.create();

        mockMvc.perform(get("/api/test/me").cookie(testMembers.loginCookies(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginCookiesAreSharedAcrossBlogAddresses() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieManager.login(response, testMembers.create(), true);

        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2).allSatisfy(cookie -> assertThat(cookie)
                .contains("Domain=.blog.test", "HttpOnly", "SameSite=Lax", "Path=/"));
        assertThat(setCookies).filteredOn(cookie -> cookie.startsWith("refresh_token=")).singleElement()
                .satisfies(cookie -> assertThat(cookie).contains("Max-Age=1209600"));
        assertThat(setCookies).filteredOn(cookie -> cookie.startsWith("access_token=")).singleElement()
                .satisfies(cookie -> assertThat(cookie).doesNotContain("Max-Age"));
    }

    @Test
    void refreshCookieWithoutRememberMeIsSessionCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieManager.login(response, testMembers.create(), false);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(cookie -> cookie.contains("Max-Age"));
    }

    @Test
    void refreshTokenRenewsMissingAccessToken() throws Exception {
        Member member = testMembers.create();
        Cookie refreshOnly = cookie(testMembers.loginCookies(member, true), AuthCookieManager.REFRESH_COOKIE);

        mockMvc.perform(get("/api/test/me").cookie(refreshOnly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=")));
    }

    @Test
    void brokenTokenIsTreatedAsAnonymousAndCleared() throws Exception {
        mockMvc.perform(get("/api/test/public").cookie(new Cookie(AuthCookieManager.ACCESS_COOKIE, "broken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void logoutInvalidatesBothTokens() throws Exception {
        Cookie[] cookies = testMembers.loginCookies(testMembers.create(), true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);

        cookieManager.logout(request, new MockHttpServletResponse());

        mockMvc.perform(get("/api/test/me").cookie(cookies))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAreaIsForAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/admin/test").cookie(testMembers.loginCookies(testMembers.create())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/test").cookie(testMembers.loginCookies(testMembers.admin())))
                .andExpect(status().isOk());
    }

    @Test
    void stateChangingApiNeedsCsrfHeader() throws Exception {
        mockMvc.perform(post("/api/test/echo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        mockMvc.perform(post("/api/test/echo").header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE))
                .andExpect(status().isOk());
    }

    @Test
    void suspendedMemberIsBlockedFromNextRequestWithReason() throws Exception {
        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member);
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED', suspended_until = '2099-01-01 00:00:00' "
                + "WHERE id = ?", member.getId());
        moderationLogRepository.save(ModerationLog.record(testMembers.admin(), ModerationAction.SUSPEND,
                ModerationTargetType.MEMBER, member.getId(), SanctionReason.SPAM, null));

        MvcResult result = mockMvc.perform(get("/api/test/public").cookie(cookies))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.detail.reason").value("SPAM"))
                .andExpect(jsonPath("$.detail.reasonMessage").value("스팸·광고"))
                .andExpect(jsonPath("$.detail.suspendedUntil").value("2099-01-01T00:00:00+09:00"))
                .andReturn();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2).allMatch(cookie -> cookie.contains("Max-Age=0"));
    }

    @Test
    void permanentSuspensionHasNullUntil() throws Exception {
        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member);
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", member.getId());

        mockMvc.perform(get("/api/test/public").cookie(cookies))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail.suspendedUntil").value(nullValue()));
    }

    @Test
    void expiredSuspensionIsNotBlocked() throws Exception {
        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member);
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED', suspended_until = '2000-01-01 00:00:00' "
                + "WHERE id = ?", member.getId());

        mockMvc.perform(get("/api/test/me").cookie(cookies))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawnMemberIsLoggedOut() throws Exception {
        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member);
        jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", member.getId());

        mockMvc.perform(get("/api/test/me").cookie(cookies))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    private Cookie cookie(Cookie[] cookies, String name) {
        return Arrays.stream(cookies).filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

}
