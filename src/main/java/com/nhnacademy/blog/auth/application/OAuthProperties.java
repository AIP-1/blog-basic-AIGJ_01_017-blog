package com.nhnacademy.blog.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 앱 키 (T099). 구글 로그인은 운영 주소가 https가 아니라 쓸 수 없어 뺐다(2026-10-12 지원 결정). 비밀 값이라 git에 올리지 않는다: 개발은 config/application-dev.yml(틀은 .example),
 * 운영은 환경 변수. 값이 비어 있으면 카카오 버튼은 "지금은 쓸 수 없습니다"로 돌아온다.
 *
 * @param kakao 카카오 developers의 REST API 키·클라이언트 시크릿·리다이렉트 URI
 */
@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(Provider kakao) {

    /**
     * @param redirectUri 제공사 콘솔에 등록한 주소와 글자 하나까지 같아야 한다
     */
    public record Provider(String clientId, String clientSecret, String redirectUri) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && redirectUri != null && !redirectUri.isBlank();
        }

    }

}
