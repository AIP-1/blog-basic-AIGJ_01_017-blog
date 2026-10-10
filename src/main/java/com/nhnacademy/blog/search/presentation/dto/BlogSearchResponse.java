package com.nhnacademy.blog.search.presentation.dto;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import com.nhnacademy.blog.search.application.FoundBlog;

/**
 * 블로그 검색 결과 한 줄 (contracts/rest-api.md `[{ blog, owner, subscriberCount }]`, SRCH-02).
 */
public record BlogSearchResponse(BlogInfo blog, MemberSummaryResponse owner, long subscriberCount) {

    public record BlogInfo(Long id, String address, String name, String description, String profileImageUrl) {
    }

    public static BlogSearchResponse from(FoundBlog found) {
        Blog blog = found.blog();
        return new BlogSearchResponse(
                new BlogInfo(blog.getId(), blog.getAddress(), blog.getName(), blog.getDescription(),
                        found.profileImageUrl()),
                MemberSummaryResponse.of(blog.getMember(), found.ownerPrimaryBlogAddress(),
                        found.ownerProfileImageUrl()),
                found.subscriberCount());
    }

}
