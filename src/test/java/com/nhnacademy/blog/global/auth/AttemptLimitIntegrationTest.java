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
 * 같은 IP는 셋을 합쳐 15분 안에 20번. 다른 테스트의 요청은 요청마다 다른 IP다(TestWebConfiguration).
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

    @Test
    void sameIpIsLockedAfterTwentyFailuresAcrossDifferentEmails() throws Exception {
        String ip = "192.0.2." + (1 + (int) (Math.random() * 250));
        Member member = testMembers.createWithPassword("right1234");
        // 비밀번호 하나를 이메일 20개에 돌려 본다(이메일마다 1번이라 이메일별 제한에는 안 걸림)
        for (int i = 0; i < 19; i++) {
            login(TestEmails.unique(), "wrong1234", ip).andExpect(status().isUnauthorized());
        }
        // 맞혀도 IP 횟수는 지우지 않는다
        login(member.getEmail(), "right1234", ip).andExpect(status().isOk());
        login(TestEmails.unique(), "wrong1234", ip).andExpect(status().isUnauthorized());

        String body = login(TestEmails.unique(), "wrong1234", ip)
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.detail.retryAfterSeconds")).longValue()).isBetween(890L, 900L);
        // 같은 IP는 맞는 비밀번호도 막힌다. 막혀서 확인하지 않은 시도는 그 이메일의 횟수에 들어가지 않는다
        login(member.getEmail(), "right1234", ip).andExpect(status().isTooManyRequests());
        assertThat(redis.opsForValue().get("attempt:login:" + member.getEmail())).isEqualTo("0");
        // 다른 IP는 상관없다
        login(member.getEmail(), "right1234", "198.51.100.7").andExpect(status().isOk());
        redis.delete("attempt:ip:" + ip);
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
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
