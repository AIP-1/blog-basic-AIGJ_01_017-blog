package com.nhnacademy.blog.admin.presentation.dto;

/** 신고 `{ targetType: POST|COMMENT|BLOG, targetId, reason, description? }`. */
public record ReportRequest(String targetType, Long targetId, String reason, String description) {
}
