package com.nhnacademy.blog.bookmark.presentation.dto;

/** 글 상세의 저장·취소 결과 `{ bookmarked }`. 저장 수는 보여 주지 않는다(SOC-03). */
public record BookmarkResponse(boolean bookmarked) {
}
