package com.nhnacademy.blog.auth.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 소셜 로그인 제공사 (AUTH-01, OWN-03). 주소에는 소문자(kakao)로, DB에는 대문자로 쓴다.
 * 구글은 리다이렉트 URI가 https여야 하는데 운영 주소가 http라 뺐다(2026-10-12 지원 결정). DB의 CHECK에는 GOOGLE이 남아 있다.
 */
public enum SocialProvider {

    KAKAO;

    /** 주소의 {provider}. 모르는 값이면 빈 값. */
    public static Optional<SocialProvider> fromPath(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (SocialProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(value)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    public String path() {
        return name().toLowerCase(Locale.ROOT);
    }

}
