package com.nhnacademy.blog.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** 메일 링크로 새 비밀번호 정하기 (OWN-02). 토큰이 틀린 것은 400 RESET_TOKEN_INVALID로, 비밀번호 규칙은 서비스의 PasswordRule이 본다. */
public record PasswordResetConfirmRequest(
        String token,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        String newPassword) {
}
