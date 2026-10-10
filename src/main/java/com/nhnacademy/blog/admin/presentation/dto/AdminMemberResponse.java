package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.application.AdminMemberService;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.member.domain.Member;
import java.time.OffsetDateTime;

/**
 * 관리자 회원 목록 한 줄. status는 지금 기준(정지 기간이 지났으면 ACTIVE). suspendedUntil은 정지 중일 때만, 영구면 null.
 * email이 null이면 소셜 가입(또는 탈퇴) 회원이다.
 */
public record AdminMemberResponse(Long id, String nickname, String email, String role, String status,
                                  OffsetDateTime suspendedUntil, OffsetDateTime createdAt) {

    public static AdminMemberResponse from(AdminMemberService.MemberRow row) {
        Member member = row.member();
        boolean suspended = row.status().name().equals("SUSPENDED");
        return new AdminMemberResponse(member.getId(), member.getNickname(), member.getEmail(), member.getRole().name(),
                row.status().name(), suspended ? DateTimes.toOffset(member.getSuspendedUntil()) : null,
                DateTimes.toOffset(member.getCreatedAt()));
    }

}
