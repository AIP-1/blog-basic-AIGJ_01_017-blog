package com.nhnacademy.blog.blog.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 블로그 개설 요청. 주소 규칙은 서비스가 본다(400 BLOG_ADDRESS_INVALID). */
public record BlogCreateRequest(
        @NotNull(message = "블로그 주소를 입력해 주세요.")
        String address,

        @NotBlank(message = "블로그 이름을 입력해 주세요.")
        @Size(max = 50, message = "블로그 이름은 50자까지입니다.")
        String name,

        @Size(max = 500, message = "소개는 500자까지입니다.")
        String description) {
}
