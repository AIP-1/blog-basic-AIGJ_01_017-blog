package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.SocialProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 구글 로그인 (T099). OpenID Connect의 scope=openid만 받는다. 인가 코드 → 토큰(oauth2.googleapis.com/token)
 * → 회원 번호(userinfo의 sub). 이메일·프로필은 받지 않는다(R-07).
 */
@Component
public class GoogleOAuthClient implements OAuthClient {

    private final OAuthProperties.Provider properties;
    private final RestClient restClient = RestClient.create();

    public GoogleOAuthClient(OAuthProperties properties) {
        this.properties = properties.google();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public boolean configured() {
        return properties != null && properties.configured();
    }

    @Override
    public String authorizeUrl(String state, boolean reauth) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", "openid")
                .queryParam("state", state)
                .queryParam("prompt", "select_account");
        if (reauth) {
            // 탈퇴 본인 확인: 이미 로그인해 있어도 다시 확인하게 한다(OpenID Connect max_age=0)
            uri.queryParam("max_age", "0");
        }
        return uri.encode().toUriString();
    }

    @Override
    public String fetchUserId(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        TokenResponse token = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.access_token() == null) {
            throw new IllegalStateException("구글이 토큰을 주지 않았습니다");
        }
        UserInfo user = restClient.get()
                .uri("https://openidconnect.googleapis.com/v1/userinfo")
                .header("Authorization", "Bearer " + token.access_token())
                .retrieve()
                .body(UserInfo.class);
        if (user == null || user.sub() == null) {
            throw new IllegalStateException("구글이 회원 번호를 주지 않았습니다");
        }
        return user.sub();
    }

    record TokenResponse(String access_token) {
    }

    record UserInfo(String sub) {
    }

}
