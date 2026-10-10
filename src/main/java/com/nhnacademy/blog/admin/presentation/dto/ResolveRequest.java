package com.nhnacademy.blog.admin.presentation.dto;

/** 신고 처리 `{ result, reason?, reasonDetail?, period? }`. 기각이 아니면 사유가, 정지면 기간이 필요하다. */
public record ResolveRequest(String result, String reason, String reasonDetail, String period) {
}
