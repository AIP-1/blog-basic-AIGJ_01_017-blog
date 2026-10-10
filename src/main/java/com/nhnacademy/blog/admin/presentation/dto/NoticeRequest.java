package com.nhnacademy.blog.admin.presentation.dto;

/** 공지 쓰기·고치기 `{ title, content }`. */
public record NoticeRequest(String title, String content) {
}
