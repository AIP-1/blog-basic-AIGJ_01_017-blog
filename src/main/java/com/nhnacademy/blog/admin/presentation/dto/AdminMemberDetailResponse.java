package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.application.AdminMemberService;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.SuspensionDetail;
import java.util.List;

/** 관리자 회원 상세 (ADMIN-02): 회원, 보유 블로그(지운 것 포함), 받은 신고 수, 지금 정지 사유, 제재 이력. */
public record AdminMemberDetailResponse(AdminMemberResponse member, List<BlogLine> blogs, long reportCount,
                                        SuspensionDetail suspension, List<ModerationLogResponse> moderations) {

    public static AdminMemberDetailResponse from(AdminMemberService.MemberDetail detail) {
        return new AdminMemberDetailResponse(AdminMemberResponse.from(detail.row()),
                detail.blogs().stream().map(BlogLine::from).toList(), detail.reportCount(), detail.suspension(),
                detail.moderations().stream().map(ModerationLogResponse::from).toList());
    }

    public record BlogLine(Long id, String address, String name, boolean isPrimary, boolean deleted,
                           boolean restricted) {

        static BlogLine from(Blog blog) {
            return new BlogLine(blog.getId(), blog.getAddress(), blog.getName(), blog.isPrimary(), blog.isDeleted(),
                    blog.isRestricted());
        }

    }

}
