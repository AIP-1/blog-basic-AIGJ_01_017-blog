package com.nhnacademy.blog.admin.presentation.dto;

/** 숨김·제한 요청 `{ reason, reasonDetail? }`. 값 검사는 Sanction.of가 한다(목록 밖 사유, 기타의 설명). */
public record SanctionRequest(String reason, String reasonDetail) {
}
