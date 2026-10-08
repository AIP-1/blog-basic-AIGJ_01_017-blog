package com.nhnacademy.blog.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. 형식이 틀린 이메일도 401 LOGIN_FAILED로 같게 답하려고 이메일 형식은 보지 않는다.
 * rememberMe는 생략할 수 있어 Boolean이다. Jackson 3은 boolean 같은 기본형 칸이 본문에 없으면 오류로 본다.
 */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password,

        Boolean rememberMe) {

    /** 생략하면 로그인 유지를 고르지 않은 것이다. */
    public boolean keepLoggedIn() {
        return Boolean.TRUE.equals(rememberMe);
    }

}
