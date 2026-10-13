package com.nhnacademy.blog.auth.domain;

import java.util.Locale;
import java.util.Optional;

/** 소셜 로그인 제공사 (AUTH-01, OWN-03). 주소에는 소문자(kakao, google)로, DB에는 대문자로 쓴다. */
public enum SocialProvider {

    KAKAO,
    GOOGLE;

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
