package com.nhnacademy.blog.auth.domain;

import com.nhnacademy.blog.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 비밀번호 규칙 (AUTH-01). 8자 이상, 영문과 숫자를 함께 쓴다.
 * bcrypt는 72바이트까지만 쓰므로 그보다 긴 비밀번호는 받지 않는다(한글은 한 글자에 3바이트).
 */
public final class PasswordRule {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private PasswordRule() {
    }

    /** 규칙에 맞지 않으면 400 VALIDATION_FAILED(field)를 던진다. */
    public static void check(String field, String password) {
        if (password == null || password.length() < MIN_LENGTH
                || !LETTER.matcher(password).find() || !DIGIT.matcher(password).find()) {
            throw BusinessException.invalidField(field, "비밀번호는 8자 이상, 영문과 숫자를 함께 써 주세요.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw BusinessException.invalidField(field, "비밀번호가 너무 깁니다.");
        }
    }

}
