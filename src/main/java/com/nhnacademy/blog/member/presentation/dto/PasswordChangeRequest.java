package com.nhnacademy.blog.member.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** 비밀번호 바꾸기 `{ currentPassword, newPassword }` (AUTH-05). 새 비밀번호 규칙은 서비스의 PasswordRule이 본다. */
public record PasswordChangeRequest(
        @NotBlank(message = "지금 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        String newPassword) {
}
