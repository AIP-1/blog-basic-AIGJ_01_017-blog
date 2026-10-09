package com.nhnacademy.blog.category.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 카테고리 추가·이름 변경 요청 (CAT-01). 이름은 앞뒤 공백을 빼고 1~30자.
 * parentId는 추가할 때만 받는다(하위 카테고리, CAT-03). 이름 변경에서 보내면 400이다
 * (상위를 바꾸는 것은 드래그 순서 바꾸기 CAT-04, 백로그).
 */
public record CategoryRequest(
        @NotBlank(message = "카테고리 이름을 입력해 주세요.")
        @Size(max = 30, message = "카테고리 이름은 30자까지입니다.")
        String name,

        Long parentId) {
}
