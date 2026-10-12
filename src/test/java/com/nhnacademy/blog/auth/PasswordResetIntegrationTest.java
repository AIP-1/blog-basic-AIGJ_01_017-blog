package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.RecordingEmailSender;
import com.nhnacademy.blog.support.TestEmails;
import com.nhnacademy.blog.support.TestMembers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호 재설정과 메일로 잠금 풀기 (T088, OWN-02, R-17, spec US5 시나리오 9).
 */
class PasswordResetIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "password1";
    private static final String NEW_PASSWORD = "newpassword2";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    RecordingEmailSender mailbox;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void linkFromMailSetsNewPasswordOnlyOnce() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);

        requestReset(member.getEmail()).andExpect(status().isAccepted());
        assertThat(mailbox.awaitTo(member.getEmail(), 1)).singleElement()
                .satisfies(mail -> assertThat(mail.text()).contains("http://blog.test/password-reset?token=", "30분"));
        String token = mailbox.lastResetToken(member.getEmail()).orElseThrow();
        // DB에는 원문이 아니라 해시만 있다
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE token_hash = ?",
                Integer.class, token)).isZero();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        login(member.getEmail(), PASSWORD).andExpect(status().isUnauthorized());
        login(member.getEmail(), NEW_PASSWORD).andExpect(status().isOk());

        // 두 번째로 쓰면 거절
        reset(token, "another3pass").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void expiredWrongOrBlankTokenIsRejected() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);
        requestReset(member.getEmail()).andExpect(status().isAccepted());
        mailbox.awaitTo(member.getEmail(), 1);
        String token = mailbox.lastResetToken(member.getEmail()).orElseThrow();

        jdbcTemplate.update("UPDATE password_reset_token SET expires_at = NOW() - INTERVAL 1 MINUTE WHERE member_id = ?",
                member.getId());
        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        reset("not-a-real-token", NEW_PASSWORD).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        reset("", NEW_PASSWORD).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        login(member.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void weakNewPasswordKeepsTheLinkUsable() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);
        requestReset(member.getEmail()).andExpect(status().isAccepted());
        mailbox.awaitTo(member.getEmail(), 1);
        String token = mailbox.lastResetToken(member.getEmail()).orElseThrow();

        reset(token, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void newPasswordRetiresOtherLinks() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);
        requestReset(member.getEmail()).andExpect(status().isAccepted());
        mailbox.awaitTo(member.getEmail(), 1);
        String first = mailbox.lastResetToken(member.getEmail()).orElseThrow();
        // 1분 제한은 Redis 키라 테스트에서는 직접 지우고 한 통 더 받는다
        redis.delete("password-reset:cooldown:" + member.getEmail());
        requestReset(member.getEmail()).andExpect(status().isAccepted());
        mailbox.awaitTo(member.getEmail(), 2);
        String second = mailbox.lastResetToken(member.getEmail()).orElseThrow();
        assertThat(second).isNotEqualTo(first);

        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(first, "another3pass").andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void unknownEmailGetsSameAnswerAndSameOneMinuteLimit() throws Exception {
        String unknown = TestEmails.unique();
        requestReset(unknown).andExpect(status().isAccepted());
        assertThat(mailbox.to(unknown)).isEmpty();
        // 가입하지 않은 이메일도 1분 안에 다시 요청하면 429라 응답으로 가입 여부를 알 수 없다
        requestReset(unknown).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail.retryAfterSeconds").isNumber());

        Member member = testMembers.createWithPassword(PASSWORD);
        requestReset(member.getEmail()).andExpect(status().isAccepted());
        requestReset(member.getEmail()).andExpect(status().isTooManyRequests());
        assertThat(mailbox.awaitTo(member.getEmail(), 1)).hasSize(1);
    }

    @Test
    void lockedOwnerGetsOneMailAndTheLinkUnlocksLogin() throws Exception {
        Member member = testMembers.createWithPassword(PASSWORD);
        // 다른 사람이 내 이메일로 5번 틀린다 → 다섯 번째에 막히고 안내 메일이 한 통 간다
        for (int i = 0; i < 5; i++) {
            login(member.getEmail(), "wrong-password9").andExpect(status().isUnauthorized());
        }
        login(member.getEmail(), PASSWORD).andExpect(status().isTooManyRequests());
        login(member.getEmail(), "wrong-password9").andExpect(status().isTooManyRequests());
        assertThat(mailbox.awaitTo(member.getEmail(), 1)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).contains("로그인이 잠시 막혔습니다"));

        // 본인은 메일의 링크로 비밀번호를 바꾸고 바로 들어온다
        String token = mailbox.lastResetToken(member.getEmail()).orElseThrow();
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        login(member.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void lockingAnUnknownEmailSendsNothing() throws Exception {
        String unknown = TestEmails.unique();
        for (int i = 0; i < 6; i++) {
            login(unknown, "wrong-password9");
        }
        assertThat(mailbox.to(unknown)).isEmpty();
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(put("/api/auth/password-reset")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberMe\":false}".formatted(email, password)));
    }

}
