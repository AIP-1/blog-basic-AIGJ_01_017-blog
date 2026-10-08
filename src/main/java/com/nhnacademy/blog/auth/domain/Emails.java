package com.nhnacademy.blog.auth.domain;

import java.util.Locale;

/**
 * 이메일 값 다루기. 소문자로 맞춰 같은 주소를 한 가지로 저장한다.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

}
