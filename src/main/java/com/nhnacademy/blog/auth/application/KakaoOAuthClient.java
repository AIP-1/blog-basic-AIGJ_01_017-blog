package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.SocialProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인 (T099). 인가 코드 → 토큰(kauth.kakao.com/oauth/token) → 회원 번호(kapi.kakao.com/v2/user/me의 id).
 * 동의 항목(이메일 등)은 받지 않는다. 회원 번호만 있으면 된다(제공사 이메일은 저장하지 않음, R-07).
 */
@Component
public class KakaoOAuthClient implements OAuthClient {

    private final OAuthProperties.Provider properties;
    private final RestClient restClient = RestClient.create();

    public KakaoOAuthClient(OAuthProperties properties) {
        this.properties = properties.kakao();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public boolean configured() {
        return properties != null && properties.configured();
    }

    @Override
    public String authorizeUrl(String state, boolean reauth) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("state", state);
        if (reauth) {
            uri.queryParam("prompt", "login");
        }
        return uri.encode().toUriString();
    }

    @Override
    public String fetchUserId(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.add("client_secret", properties.clientSecret());
        }
        TokenResponse token = restClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.access_token() == null) {
            throw new IllegalStateException("카카오가 토큰을 주지 않았습니다");
        }
        UserResponse user = restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + token.access_token())
                .retrieve()
                .body(UserResponse.class);
        if (user == null || user.id() == null) {
            throw new IllegalStateException("카카오가 회원 번호를 주지 않았습니다");
        }
        return String.valueOf(user.id());
    }

    record TokenResponse(String access_token) {
    }

    record UserResponse(Long id) {
    }

}
