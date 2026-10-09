package com.nhnacademy.blog.member.presentation.dto;

import jakarta.validation.constraints.Size;

/** 회원정보 수정 `{ nickname?, profileImageId? }` (AUTH-05). 보내지 않은 칸은 그대로 둔다. */
public record MeUpdateRequest(
        @Size(max = 20, message = "닉네임은 20자까지입니다.")
        String nickname,

        Long profileImageId) {
}
