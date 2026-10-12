package com.nhnacademy.blog.manage.presentation.dto;

/** 차단 `{ memberId, memo? }` (MNG-04). */
public record BlockRequest(Long memberId, String memo) {
}
