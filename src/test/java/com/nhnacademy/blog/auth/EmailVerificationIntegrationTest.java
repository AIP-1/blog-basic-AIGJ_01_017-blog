package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestEmails;
import com.nhnacademy.blog.support.TestMembers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 이메일 인증 코드 발송·확인 (T017, OWN-01).
 */
class EmailVerificationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestEmails testEmails;

    @Autowired
    TestMembers testMembers;

    @Test
    void sendsSixDigitCodeAndVerifiesIt() throws Exception {
        String email = TestEmails.unique();

        send(email).andExpect(status().isAccepted());
        String code = testEmails.latestCode(email);
        assertThat(code).matches("\\d{6}");

        verify(email, code).andExpect(status().isOk());
        // 화면 단계 확인은 코드를 쓰지 않으므로 다시 확인해도 된다
        verify(email, code).andExpect(status().isOk());
    }

    @Test
    void emailIsComparedIgnoringCase() throws Exception {
        String email = TestEmails.unique();

        send(email.toUpperCase()).andExpect(status().isAccepted());

        verify(email, testEmails.latestCode(email)).andExpect(status().isOk());
    }

    @Test
    void wrongCodeIsRejected() throws Exception {
        String email = TestEmails.unique();
        send(email);
        String wrong = testEmails.latestCode(email).equals("000000") ? "111111" : "000000";

        verify(email, wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        verify(TestEmails.unique(), "123456")
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String email = TestEmails.unique();
        send(email);
        testEmails.expireLatest(email);

        verify(email, testEmails.latestCode(email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    void resendWithinOneMinuteIs429() throws Exception {
        String email = TestEmails.unique();
        send(email).andExpect(status().isAccepted());

        send(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.detail.retryAfterSeconds").isNumber());
    }

    @Test
    void registeredEmailIs409() throws Exception {
        Member member = testMembers.create();

        send(member.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void invalidInputIs400() throws Exception {
        send("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        verify(TestEmails.unique(), "12ab")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
    }

    private ResultActions send(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/email-verifications")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions verify(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/email-verifications/verify")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

}
