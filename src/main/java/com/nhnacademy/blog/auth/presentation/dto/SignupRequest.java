package com.nhnacademy.blog.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 가입 요청. 비밀번호 규칙은 서비스의 PasswordRule이 본다. */
public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일이 너무 깁니다.")
        String email,

        @NotBlank(message = "인증 코드를 입력해 주세요.")
        @Pattern(regexp = "\\d{6}", message = "인증 코드는 숫자 6자리입니다.")
        String code,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자까지입니다.")
        String nickname) {
}
