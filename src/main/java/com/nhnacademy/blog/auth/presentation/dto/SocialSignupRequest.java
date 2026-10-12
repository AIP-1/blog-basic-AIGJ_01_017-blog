package com.nhnacademy.blog.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 처음 보는 소셜 계정의 닉네임 확인 뒤 가입 (AUTH-01). signupToken은 콜백이 준 10분짜리 값이다. */
public record SocialSignupRequest(
        String signupToken,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자까지입니다.")
        String nickname) {
}
