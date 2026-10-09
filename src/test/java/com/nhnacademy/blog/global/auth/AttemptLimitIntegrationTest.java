package com.nhnacademy.blog.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestEmails;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호·인증 코드 시도 제한 (T055a, R-17). 15분 안에 5번 틀리면 다섯 번째로 틀린 때부터 15분 동안 429.
 */
class AttemptLimitIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestEmails testEmails;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void fiveWrongPasswordsLockLoginForFifteenMinutesEvenForTheRightPassword() throws Exception {
        Member member = testMembers.createWithPassword("right1234");
        for (int i = 0; i < 5; i++) {
            login(member.getEmail(), "wrong1234").andExpect(status().isUnauthorized());
        }

        String body = login(member.getEmail(), "right1234")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andReturn().getResponse().getContentAsString();
        long retryAfter = ((Number) JsonPath.read(body, "$.detail.retryAfterSeconds")).longValue();
        assertThat(retryAfter).isBetween(890L, 900L);

        // 15분이 지나 키가 사라지면 다시 로그인된다
        redis.delete("attempt:login:" + member.getEmail());
        login(member.getEmail(), "right1234").andExpect(status().isOk());
    }

    @Test
    void successResetsTheCount() throws Exception {
        Member member = testMembers.createWithPassword("right1234");
        for (int i = 0; i < 4; i++) {
            login(member.getEmail(), "wrong1234").andExpect(status().isUnauthorized());
        }
        login(member.getEmail(), "right1234").andExpect(status().isOk());
        assertThat(redis.hasKey("attempt:login:" + member.getEmail())).isFalse();
        for (int i = 0; i < 4; i++) {
            login(member.getEmail(), "wrong1234").andExpect(status().isUnauthorized());
        }
        login(member.getEmail(), "right1234").andExpect(status().isOk());
    }

    @Test
    void unknownEmailIsLimitedTheSameWaySoItRevealsNothing() throws Exception {
        String email = TestEmails.unique();
        for (int i = 0; i < 5; i++) {
            login(email, "wrong1234").andExpect(status().isUnauthorized());
        }
        login(email, "wrong1234").andExpect(status().isTooManyRequests());
    }

    @Test
    void concurrentGuessesGetOnlyFiveTries() throws Exception {
        Member member = testMembers.createWithPassword("right1234");
        List<Callable<Integer>> guesses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            guesses.add(() -> login(member.getEmail(), "wrong1234").andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
            for (Future<Integer> result : executor.invokeAll(guesses)) {
                statuses.add(result.get());
            }
        }
        assertThat(statuses).filteredOn(code -> code == 401).hasSize(5);
        assertThat(statuses).filteredOn(code -> code == 429).hasSize(7);
    }

    @Test
    void fiveWrongVerificationCodesLockTheEmailEvenForTheRightCode() throws Exception {
        String email = TestEmails.unique();
        mockMvc.perform(post("/api/auth/email-verifications").header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        String code = testEmails.latestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            verify(email, wrong).andExpect(status().isBadRequest());
        }
        verify(email, code).andExpect(status().isTooManyRequests());
    }

    @Test
    void fiveWrongCurrentPasswordsLockPasswordChange() throws Exception {
        Member member = testMembers.createWithPassword("right1234");
        Cookie[] cookies = testMembers.loginCookies(member);
        for (int i = 0; i < 5; i++) {
            changePassword(cookies, "wrong1234").andExpect(status().isBadRequest());
        }
        changePassword(cookies, "right1234").andExpect(status().isTooManyRequests());
        // 다른 회원은 상관없다
        Member other = testMembers.createWithPassword("right1234");
        changePassword(testMembers.loginCookies(other), "right1234").andExpect(status().isNoContent());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions verify(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/email-verifications/verify")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)));
    }

    private ResultActions changePassword(Cookie[] cookies, String current) throws Exception {
        return mockMvc.perform(put("/api/me/password")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"newpass123\"}".formatted(current))
                .cookie(cookies));
    }

}
