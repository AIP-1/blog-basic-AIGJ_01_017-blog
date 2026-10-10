package com.nhnacademy.blog.blog.presentation.dto;

import com.nhnacademy.blog.blog.application.BlogDetail;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;

/**
 * Blog (contracts/rest-api.md 주요 응답 객체). GET /api/blog, 개설·수정 응답.
 * restriction은 주인에게만, 이용 제한 중일 때 사유다. profileImageUrl은 프로필 이미지의 썸네일 주소, 없으면 null.
 */
public record BlogResponse(Long id, String address, String name, String description, String profileImageUrl,
                           MemberSummaryResponse owner, String skin, String listLayout, String accentColor,
                           long postCount, long subscriberCount, Viewer viewer, Restriction restriction) {

    public record Viewer(boolean isOwner, boolean subscribed) {
    }

    public record Restriction(String reason, String reasonMessage) {
    }

    public static BlogResponse from(BlogDetail detail) {
        Blog blog = detail.blog();
        Restriction restriction = detail.restriction() == null
                ? null
                : new Restriction(detail.restriction().reason(), detail.restriction().reasonMessage());
        return new BlogResponse(blog.getId(), blog.getAddress(), blog.getName(), blog.getDescription(),
                detail.profileImageUrl(),
                MemberSummaryResponse.of(blog.getMember(), detail.ownerPrimaryBlogAddress(),
                        detail.ownerProfileImageUrl()), blog.getSkin(),
                blog.getListLayout().name(), blog.getAccentColor().name(), detail.postCount(),
                detail.subscriberCount(), new Viewer(detail.owner(), detail.subscribed()), restriction);
    }

}
