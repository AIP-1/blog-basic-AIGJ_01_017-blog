package com.nhnacademy.blog.auth.application;

import java.util.Locale;

/**
 * 소셜 인증을 하는 까닭 (contracts 소셜 로그인 흐름). login: 로그인·처음이면 가입, link: 마이페이지 연동(OWN-03),
 * reauth: 소셜로 가입한 회원의 탈퇴 본인 확인(AUTH-06).
 */
public enum OAuthMode {

    LOGIN,
    LINK,
    REAUTH;

    /** 주소의 mode. 없거나 모르는 값이면 login. */
    public static OAuthMode fromParam(String value) {
        if (value == null) {
            return LOGIN;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LOGIN;
        }
    }

}
