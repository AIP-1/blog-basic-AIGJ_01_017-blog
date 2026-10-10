package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;

/**
 * 블로그 화면 머리글에 필요한 것 (GET /api/blog).
 *
 * @param profileImageUrl         블로그 프로필 이미지의 썸네일 주소. 없으면 null
 * @param ownerPrimaryBlogAddress 주인의 대표 블로그 주소. 없거나 보는 사람이 볼 수 없으면 null
 * @param postCount               보는 사람이 볼 수 있는 글 수
 * @param restriction             주인에게만, 이용 제한 중일 때 사유. 아니면 null
 */
public record BlogDetail(Blog blog, String profileImageUrl, String ownerPrimaryBlogAddress, long postCount, long subscriberCount,
                         boolean owner, boolean subscribed, Restriction restriction) {

    public record Restriction(String reason, String reasonMessage) {
    }

}
