package com.nhnacademy.blog.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** 로그인 요청. 형식이 틀린 이메일도 401 LOGIN_FAILED로 같게 답하려고 이메일 형식은 보지 않는다. */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password,

        boolean rememberMe) {
}
