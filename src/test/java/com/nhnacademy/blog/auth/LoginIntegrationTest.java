package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.auth.presentation.AuthCookieManager;
import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
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
 * 로그인 (T019, AUTH-01, AUTH-03, ADMIN-02, spec US1 수용 시나리오 3·4).
 */
class LoginIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "password1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    ModerationLogRepository moderationLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void loginIssuesCookiesSharedByAllBlogAddresses() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);

        MvcResult result = login(member.getEmail(), PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.nickname").value(member.getNickname()))
                .andReturn();

        // 로그인 쿠키 둘(Access·Refresh). 기기 쿠키(T055b)는 로그인 상태가 아니라서 따로 본다
        List<String> setCookies = loginCookies(result);
        // 블로그도 플랫폼 주소 아래 /@주소라 Domain 없이 이 주소 하나에만 둔다(스텝 21)
        assertThat(setCookies).hasSize(2).allSatisfy(cookie -> assertThat(cookie).doesNotContain("Domain="));
        // 로그인 유지를 고르지 않으면 브라우저를 닫으면 사라지는 쿠키다
        assertThat(setCookies).noneMatch(cookie -> cookie.contains("Max-Age"));

        // 플랫폼에서 로그인한 쿠키로 블로그 화면(/@alpha)이 부르는 API에서도 로그인 상태다 (시나리오 4)
        mockMvc.perform(get("/api/me").header(TestBlogs.HEADER, "alpha")
                        .cookie(result.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()));
    }

    @Test
    void rememberMeKeepsRefreshCookieFor14Days() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);

        MvcResult result = login(member.getEmail(), PASSWORD, true).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .filteredOn(cookie -> cookie.startsWith("refresh_token="))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie).contains("Max-Age=1209600"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetSameAnswer() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);

        login(member.getEmail(), "password2", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 맞지 않습니다."));
        login("nobody-" + member.getEmail(), PASSWORD, false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 맞지 않습니다."));
        login(member.getEmail(), "a1".repeat(50), false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);

        login(member.getEmail().toUpperCase(), PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void withdrawnMemberIsTreatedAsUnknown() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);
        jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = NOW() WHERE id = ?",
                member.getId());

        login(member.getEmail(), PASSWORD, false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void suspendedMemberGetsReasonOnlyWithRightPassword() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED', suspended_until = NOW() + INTERVAL 7 DAY"
                + " WHERE id = ?", member.getId());
        moderationLogRepository.save(ModerationLog.record(testMembers.admin(), ModerationAction.SUSPEND,
                ModerationTargetType.MEMBER, member.getId(), SanctionReason.SPAM, null));

        login(member.getEmail(), "password2", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        login(member.getEmail(), PASSWORD, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.detail.reason").value("SPAM"))
                .andExpect(jsonPath("$.detail.reasonMessage").isString())
                .andExpect(jsonPath("$.detail.suspendedUntil").isString());
    }

    @Test
    void rememberMeCanBeOmitted() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(loginCookies(result)).noneMatch(c -> c.contains("Max-Age"));
    }

    /** 기기 쿠키(device=…, 1년)를 뺀 로그인 쿠키. */
    private static List<String> loginCookies(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> !cookie.startsWith(AuthCookieManager.DEVICE_COOKIE + "="))
                .toList();
    }

    @Test
    void missingFieldsAre400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions login(String email, String password, boolean rememberMe) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","rememberMe":%s}
                        """.formatted(email, password, rememberMe)));
    }

}
