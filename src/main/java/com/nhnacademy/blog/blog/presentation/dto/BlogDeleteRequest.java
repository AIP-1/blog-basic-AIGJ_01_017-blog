package com.nhnacademy.blog.blog.presentation.dto;

/** 블로그 삭제 확인 `{ confirmAddress }`: 블로그 주소를 다시 입력한다 (BLOG-07). */
public record BlogDeleteRequest(String confirmAddress) {
}
