package com.nhnacademy.blog.category.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * 카테고리 추가·이름 변경 요청 (CAT-01). 이름은 앞뒤 공백을 빼고 1~30자.
 * parentId(하위 카테고리, CAT-03)는 스텝 9에서 받는다. 그 전에는 보내면 400이다.
 */
public record CategoryRequest(
        @NotBlank(message = "카테고리 이름을 입력해 주세요.")
        @Size(max = 30, message = "카테고리 이름은 30자까지입니다.")
        String name,

        @Null(message = "하위 카테고리는 아직 만들 수 없습니다.")
        Long parentId) {
}
