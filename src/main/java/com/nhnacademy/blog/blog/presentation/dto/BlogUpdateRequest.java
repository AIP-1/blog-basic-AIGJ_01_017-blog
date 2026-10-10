package com.nhnacademy.blog.blog.presentation.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 블로그 정보 수정 (BLOG-02). 보낸 항목만 바꾼다. 주소는 바꿀 수 없어 받지 않는다.
 * 소개를 지우려면 빈 문자열을 보낸다. profileImageId는 주인이 올린 이미지(POST /api/images)의 id다.
 * 꾸미기(BLOG-05, 백로그)는 기능이 생기면 더한다.
 */
public record BlogUpdateRequest(
        @Size(min = 1, max = 50, message = "블로그 이름은 1~50자입니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "블로그 이름을 입력해 주세요.")
        String name,

        @Size(max = 500, message = "소개는 500자까지입니다.")
        String description,

        Long profileImageId,

        /** 꾸미기 (BLOG-05). 값 검사는 BlogService가 한다(모르는 값이면 그 칸 400). */
        String skin,
        String listLayout,
        String accentColor) {
}
