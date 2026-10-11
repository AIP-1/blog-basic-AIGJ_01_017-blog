package com.nhnacademy.blog.manage.presentation.dto;

import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import java.time.OffsetDateTime;

/** 차단한 회원 한 줄 `{ member: MemberSummary, memo, blockedAt }`. */
public record BlockedMemberResponse(MemberSummaryResponse member, String memo, OffsetDateTime blockedAt) {
}
