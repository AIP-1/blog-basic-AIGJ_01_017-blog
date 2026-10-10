package com.nhnacademy.blog.admin.presentation.dto;

/** 정지 요청 `{ period: 7D|30D|PERMANENT, reason, reasonDetail? }`. */
public record SuspensionRequest(String period, String reason, String reasonDetail) {
}
