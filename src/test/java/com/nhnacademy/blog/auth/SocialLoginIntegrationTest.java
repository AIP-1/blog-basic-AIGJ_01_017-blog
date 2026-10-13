package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.auth.presentation.AuthCookieManager;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 소셜 로그인·가입·연동·해제·재인증 (T099, T100, AUTH-01 소셜, OWN-03, AUTH-06, spec US10 시나리오 10·11, R-07).
 * 제공사는 가짜(FakeOAuthClients): 콜백의 code가 곧 제공사 회원 번호다. 테스트마다 다른 번호를 쓴다.
 */
class SocialLoginIntegrationTest extends IntegrationTestSupport {

    private static final Pattern STATE = Pattern.compile("state=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void firstSocialLoginAsksNicknameThenSignsUpAndNextTimeLogsIn() throws Exception {
        String kakaoId = uniqueId();

        Flow first = flow("kakao", "login", null, kakaoId, new Cookie[0]);
        assertThat(first.location()).startsWith("/signup/social?token=").endsWith("&provider=kakao");
        assertThat(first.loginCookies()).isEmpty();
        String token = first.location().replaceAll(".*token=([^&]+).*", "$1");

        String nickname = "소셜" + kakaoId.substring(0, 6);
        MvcResult signup = mockMvc.perform(post("/api/auth/oauth/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"%s\",\"nickname\":\"%s\"}".formatted(token, nickname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.socialAccounts[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.socialAccounts[0].linkedAt").isString())
                .andReturn();
        assertThat(signup.getResponse().getCookie(AuthCookieManager.ACCESS_COOKIE)).isNotNull();

        // 같은 가입 토큰은 다시 못 쓴다
        mockMvc.perform(post("/api/auth/oauth/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"%s\",\"nickname\":\"다른이름\"}".formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_EXPIRED"));

        // 다음부터는 같은 카카오 계정이면 그 회원으로 로그인하고, 돌아갈 곳으로 간다
        Flow again = flow("kakao", "login", "http://blog.test/@alpha/12", kakaoId, new Cookie[0]);
        assertThat(again.location()).isEqualTo("http://blog.test/@alpha/12");
        assertThat(again.loginCookies()).isNotEmpty();
        mockMvc.perform(get("/api/me").cookie(again.loginCookies()))
                .andExpect(jsonPath("$.nickname").value(nickname));
    }

    @Test
    void takenNicknameIsRejectedAndTheTokenStaysUsable() throws Exception {
        Member existing = testMembers.create();
        Flow first = flow("google", "login", null, uniqueId(), new Cookie[0]);
        String token = first.location().replaceAll(".*token=([^&]+).*", "$1");

        mockMvc.perform(post("/api/auth/oauth/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"%s\",\"nickname\":\"%s\"}".formatted(token, existing.getNickname())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        mockMvc.perform(post("/api/auth/oauth/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"%s\",\"nickname\":\"%s\"}".formatted(token, "g" + uniqueId().substring(0, 8))))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyOurAddressesAreAllowedAsRedirect() throws Exception {
        String kakaoId = signUpWithSocial("kakao");
        assertThat(flow("kakao", "login", "https://evil.example/steal", kakaoId, new Cookie[0]).location()).isEqualTo("/");
        assertThat(flow("kakao", "login", "//evil.example", kakaoId, new Cookie[0]).location()).isEqualTo("/");
        assertThat(flow("kakao", "login", "/feed", kakaoId, new Cookie[0]).location()).isEqualTo("/feed");
    }

    @Test
    void stateMustComeFromThisBrowserAndBeUsedOnce() throws Exception {
        String kakaoId = signUpWithSocial("kakao");
        MvcResult authorize = authorize("kakao", "login", null, new Cookie[0]);
        String state = state(authorize);
        String complete = callback("kakao", kakaoId, state);
        String ticket = complete.replaceAll(".*ticket=", "");

        // 다른 사람이 자기 콜백 주소를 내게 열게 해도(내 브라우저에 그 state 쿠키가 없음) 로그인되지 않는다
        MvcResult noCookie = mockMvc.perform(get("/api/auth/oauth/complete").param("ticket", ticket)).andReturn();
        assertThat(noCookie.getResponse().getRedirectedUrl()).isEqualTo("/login?error=OAUTH_FAILED");
        assertThat(noCookie.getResponse().getCookie(AuthCookieManager.ACCESS_COOKIE)).isNull();

        // state는 한 번만: 같은 state로 콜백을 다시 오면 실패
        assertThat(callback("kakao", kakaoId, state)).isEqualTo("http://blog.test/login?error=OAUTH_FAILED");
        // 모르는 state, 다른 제공사의 state도 실패
        assertThat(callback("kakao", kakaoId, "made-up")).isEqualTo("http://blog.test/login?error=OAUTH_FAILED");
        String googleState = state(authorize("google", "login", null, new Cookie[0]));
        assertThat(callback("kakao", kakaoId, googleState)).isEqualTo("http://blog.test/login?error=OAUTH_FAILED");
    }

    @Test
    void cancelledOrFailedProviderStepGoesBackWithError() throws Exception {
        String state = state(authorize("kakao", "login", null, new Cookie[0]));
        MvcResult cancelled = mockMvc.perform(get("/api/auth/oauth/kakao/callback")
                .param("state", state).param("error", "access_denied")).andReturn();
        assertThat(cancelled.getResponse().getRedirectedUrl()).isEqualTo("http://blog.test/login?error=OAUTH_CANCELLED");

        String failState = state(authorize("kakao", "login", null, new Cookie[0]));
        assertThat(callback("kakao", "fail-exchange", failState)).isEqualTo("http://blog.test/login?error=OAUTH_FAILED");
    }

    @Test
    void suspendedMemberIsSentToLoginWithNotice() throws Exception {
        String kakaoId = signUpWithSocial("kakao");
        jdbcTemplate.update("UPDATE member m SET status = 'SUSPENDED', suspended_until = NOW() + INTERVAL '1 day' "
                + "FROM social_account s WHERE s.member_id = m.id AND s.provider_user_id = ?", kakaoId);

        Flow flow = flow("kakao", "login", null, kakaoId, new Cookie[0]);
        // 이메일 로그인의 403 MEMBER_SUSPENDED와 같은 안내를 그리도록 기한을 붙인다(사유 기록이 없으면 사유는 없음)
        assertThat(flow.location()).startsWith("/login?error=MEMBER_SUSPENDED&until=");
        assertThat(flow.loginCookies()).isEmpty();
    }

    @Test
    void linkAddsAnotherProviderAndRejectsTakenOrDuplicateProvider() throws Exception {
        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member);
        String googleId = uniqueId();

        assertThat(flow("google", "link", null, googleId, cookies).location()).isEqualTo("/me?linked=google");
        mockMvc.perform(get("/api/me").cookie(cookies))
                .andExpect(jsonPath("$.socialAccounts[0].provider").value("GOOGLE"));

        // 같은 제공사 하나 더 → 거절
        assertThat(flow("google", "link", null, uniqueId(), cookies).location())
                .isEqualTo("/me?error=PROVIDER_ALREADY_LINKED");
        // 다른 회원에 연결된 구글 계정 → 거절
        Member other = testMembers.create();
        assertThat(flow("google", "link", null, googleId, testMembers.loginCookies(other)).location())
                .isEqualTo("/me?error=SOCIAL_ALREADY_LINKED");

        // 연동한 구글로 로그인하면 이 회원이다(이메일이 같아서가 아니라 연동해서)
        Flow login = flow("google", "login", null, googleId, new Cookie[0]);
        mockMvc.perform(get("/api/me").cookie(login.loginCookies())).andExpect(jsonPath("$.id").value(member.getId()));
    }

    @Test
    void linkAndReauthNeedLogin() throws Exception {
        MvcResult link = authorize("kakao", "link", null, new Cookie[0]);
        assertThat(link.getResponse().getRedirectedUrl()).isEqualTo("http://blog.test/login?redirect=%2Fme");
        MvcResult reauth = authorize("kakao", "reauth", null, new Cookie[0]);
        assertThat(reauth.getResponse().getRedirectedUrl()).isEqualTo("http://blog.test/login?redirect=%2Fme%2Fwithdraw");
    }

    @Test
    void lastLoginMethodCannotBeUnlinked() throws Exception {
        // 이메일 회원은 소셜을 모두 해제해도 비밀번호가 남는다
        Member emailMember = testMembers.create();
        Cookie[] emailCookies = testMembers.loginCookies(emailMember);
        flow("kakao", "link", null, uniqueId(), emailCookies);
        unlink("kakao", emailCookies).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me").cookie(emailCookies)).andExpect(jsonPath("$.socialAccounts").isEmpty());

        // 소셜로만 가입한 회원: 둘 중 하나는 해제되고, 마지막 하나는 409
        String kakaoId = signUpWithSocial("kakao");
        Cookie[] socialCookies = flow("kakao", "login", null, kakaoId, new Cookie[0]).loginCookies();
        flow("google", "link", null, uniqueId(), socialCookies);
        unlink("kakao", socialCookies).andExpect(status().isNoContent());
        unlink("google", socialCookies).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD"));
        // 연결되지 않은 제공사는 할 일이 없다
        unlink("kakao", socialCookies).andExpect(status().isNoContent());
        unlink("naver", socialCookies).andExpect(status().isNotFound());
    }

    @Test
    void socialOnlyMemberWithdrawsAfterReauthAndTheAccountCanSignUpAgain() throws Exception {
        String kakaoId = signUpWithSocial("kakao");
        Cookie[] cookies = flow("kakao", "login", null, kakaoId, new Cookie[0]).loginCookies();

        // 재인증 전에는 {}로 탈퇴할 수 없다
        withdraw(cookies, "{}").andExpect(status().isUnauthorized());
        // 다른 사람의 카카오로는 본인 확인이 되지 않는다
        String strangerKakao = signUpWithSocial("kakao");
        assertThat(flow("kakao", "reauth", null, strangerKakao, cookies).location())
                .isEqualTo("/me/withdraw?error=REAUTH_FAILED");

        Flow reauth = flow("kakao", "reauth", null, kakaoId, cookies);
        assertThat(reauth.authorizeUrl()).endsWith("&reauth");
        assertThat(reauth.location()).isEqualTo("/me/withdraw?reauth=ok");
        withdraw(cookies, "{}").andExpect(status().isNoContent());

        // 탈퇴하면 소셜 연동이 끊겨, 같은 카카오 계정은 처음 보는 계정이 된다(새로 가입 가능)
        assertThat(flow("kakao", "login", null, kakaoId, new Cookie[0]).location()).startsWith("/signup/social?token=");
    }

    @Test
    void reauthMarkIsUsedOnce() throws Exception {
        Member member = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member);
        String googleId = uniqueId();
        flow("google", "link", null, googleId, cookies);
        assertThat(flow("google", "reauth", null, googleId, cookies).location()).isEqualTo("/me/withdraw?reauth=ok");
        // 이메일 회원도 비밀번호 대신 소셜 재인증으로 탈퇴할 수 있고, 비밀번호로도 여전히 된다
        withdraw(cookies, "{\"password\":\"wrong-password1\"}").andExpect(status().isUnauthorized());
        withdraw(cookies, "{}").andExpect(status().isNoContent());
    }

    // ---------- 흐름 도우미 ----------

    /** authorize → (제공사) → callback → complete 한 바퀴. */
    private Flow flow(String provider, String mode, String redirect, String providerUserId, Cookie[] cookies)
            throws Exception {
        MvcResult authorize = authorize(provider, mode, redirect, cookies);
        String state = state(authorize);
        String complete = callback(provider, providerUserId, state);
        assertThat(complete).startsWith("http://blog.test/api/auth/oauth/complete?ticket=");
        Cookie stateCookie = authorize.getResponse().getCookie(AuthCookieManager.OAUTH_STATE_COOKIE);
        List<Cookie> sent = new ArrayList<>(Arrays.asList(cookies));
        sent.add(stateCookie);
        MvcResult done = mockMvc.perform(get("/api/auth/oauth/complete")
                        .header(HttpHeaders.HOST, "blog.test")
                        .param("ticket", complete.replaceAll(".*ticket=", ""))
                        .cookie(sent.toArray(Cookie[]::new)))
                .andExpect(status().isFound())
                .andReturn();
        Cookie[] login = Arrays.stream(done.getResponse().getCookies())
                .filter(cookie -> cookie.getName().equals(AuthCookieManager.ACCESS_COOKIE)
                        || cookie.getName().equals(AuthCookieManager.REFRESH_COOKIE))
                .toArray(Cookie[]::new);
        return new Flow(authorize.getResponse().getRedirectedUrl(), done.getResponse().getRedirectedUrl(), login);
    }

    private record Flow(String authorizeUrl, String location, Cookie[] loginCookies) {
    }

    private MvcResult authorize(String provider, String mode, String redirect, Cookie[] cookies) throws Exception {
        var request = get("/api/auth/oauth/" + provider + "/authorize").header(HttpHeaders.HOST, "blog.test")
                .param("mode", mode);
        if (redirect != null) {
            request.param("redirect", redirect);
        }
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request).andExpect(status().isFound()).andReturn();
    }

    private static String state(MvcResult authorize) {
        Matcher matcher = STATE.matcher(authorize.getResponse().getRedirectedUrl());
        assertThat(matcher.find()).isTrue();
        assertThat(authorize.getResponse().getCookie(AuthCookieManager.OAUTH_STATE_COOKIE).getValue())
                .isEqualTo(matcher.group(1));
        return matcher.group(1);
    }

    /** 제공사가 콜백 주소(localhost)로 돌려보낸다. 다음 주소를 돌려준다. */
    private String callback(String provider, String code, String state) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/" + provider + "/callback")
                        .param("code", code).param("state", state))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();
    }

    /** 처음 보는 소셜 계정으로 가입하고 그 제공사 회원 번호를 돌려준다. */
    private String signUpWithSocial(String provider) throws Exception {
        String providerUserId = uniqueId();
        String location = flow(provider, "login", null, providerUserId, new Cookie[0]).location();
        String token = location.replaceAll(".*token=([^&]+).*", "$1");
        String response = mockMvc.perform(post("/api/auth/oauth/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"%s\",\"nickname\":\"%s\"}"
                                .formatted(token, "s" + providerUserId.substring(0, 10))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(response, "$.id")).isPositive();
        return providerUserId;
    }

    private org.springframework.test.web.servlet.ResultActions unlink(String provider, Cookie[] cookies)
            throws Exception {
        return mockMvc.perform(delete("/api/me/social/" + provider)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(cookies));
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(Cookie[] cookies, String body)
            throws Exception {
        return mockMvc.perform(delete("/api/me")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(cookies));
    }

    private static String uniqueId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
