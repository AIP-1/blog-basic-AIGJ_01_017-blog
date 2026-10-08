package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.support.TestEmails;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 가입과 내 정보 (T018, AUTH-01, OWN-01, spec US1 수용 시나리오 1·2).
 */
class SignupIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestEmails testEmails;

    @Autowired
    TestMembers testMembers;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void signupCreatesMemberAndLogsIn() throws Exception {
        String email = TestEmails.unique();
        String code = receiveCode(email);
        String nickname = uniqueNickname();

        MvcResult result = signup(email, code, "password1", nickname)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.primaryBlog").doesNotExist())
                .andReturn();

        Member member = memberRepository.findByEmail(email).orElseThrow();
        assertThat(member.getPasswordHash()).isNotEqualTo("password1");
        assertThat(passwordEncoder.matches("password1", member.getPasswordHash())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verified_at IS NOT NULL FROM email_verification WHERE email = ?", Boolean.class, email))
                .isTrue();

        // 가입 응답의 쿠키로 바로 로그인 상태다
        Cookie[] cookies = result.getResponse().getCookies();
        mockMvc.perform(get("/api/me").cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()));
    }

    @Test
    void noMemberWithoutVerifiedCode() throws Exception {
        String email = TestEmails.unique();
        String code = receiveCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        signup(email, wrong, "password1", uniqueNickname())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        signup(TestEmails.unique(), "123456", "password1", uniqueNickname())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));

        assertThat(memberRepository.existsByEmail(email)).isFalse();
    }

    @Test
    void codeCannotBeUsedTwice() throws Exception {
        String email = TestEmails.unique();
        String code = receiveCode(email);
        signup(email, code, "password1", uniqueNickname()).andExpect(status().isCreated());

        signup(email, code, "password1", uniqueNickname())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void duplicateEmailOrNicknameIs409() throws Exception {
        Member existing = testMembers.create();
        String email = TestEmails.unique();
        String code = receiveCode(email);

        signup(email, code, "password1", existing.getNickname())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        // 실패한 가입은 코드를 쓰지 않았으므로 같은 코드로 다시 할 수 있다
        signup(email, code, "password1", uniqueNickname()).andExpect(status().isCreated());
    }

    @Test
    void passwordNeedsEightCharsWithLettersAndDigits() throws Exception {
        String email = TestEmails.unique();
        String code = receiveCode(email);

        for (String weak : new String[] {"pass123", "password", "12345678", "가나다라마바사아1a".repeat(4)}) {
            signup(email, code, weak, uniqueNickname())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        }
        assertThat(memberRepository.existsByEmail(email)).isFalse();
    }

    @Test
    void nicknameAvailability() throws Exception {
        Member existing = testMembers.create();

        mockMvc.perform(get("/api/auth/nickname-availability").param("nickname", existing.getNickname()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(get("/api/auth/nickname-availability").param("nickname", uniqueNickname()))
                .andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(get("/api/auth/nickname-availability").param("nickname", "a".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void meIs401ForAnonymous() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private String receiveCode(String email) throws Exception {
        mockMvc.perform(post("/api/auth/email-verifications")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        return testEmails.latestCode(email);
    }

    private ResultActions signup(String email, String code, String password, String nickname) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","code":"%s","password":"%s","nickname":"%s"}
                        """.formatted(email, code, password, nickname)));
    }

    private static String uniqueNickname() {
        return "n" + UUID.randomUUID().toString().substring(0, 8);
    }

}
