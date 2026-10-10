package com.nhnacademy.blog.category.presentation.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 카테고리 이름 변경·비공개 (CAT-01, CAT-05). 보낸 칸만 바꾼다(`{ name }` 또는 `{ isPrivate }`).
 * parentId는 받지 않는다. 상위를 바꾸는 것은 순서 바꾸기(PUT /api/categories/order, CAT-04)다.
 */
public record CategoryUpdateRequest(
        @Size(min = 1, max = 30, message = "카테고리 이름은 1~30자입니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "카테고리 이름을 입력해 주세요.")
        String name,

        Boolean isPrivate,

        Long parentId) {
}
