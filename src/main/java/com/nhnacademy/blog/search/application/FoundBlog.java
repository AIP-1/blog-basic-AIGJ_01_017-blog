package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.blog.domain.Blog;

/**
 * 블로그 검색 결과 한 줄 (SRCH-02). 블로그 주인은 blog.getMember()로 읽혀 있다.
 *
 * @param profileImageUrl         블로그 프로필 이미지 썸네일. 없으면 null
 * @param ownerProfileImageUrl    주인의 회원 프로필 사진 썸네일. 없으면 null
 * @param ownerPrimaryBlogAddress 주인의 대표 블로그 주소. 없거나 볼 수 없으면 null
 */
public record FoundBlog(Blog blog, long subscriberCount, String profileImageUrl, String ownerProfileImageUrl,
                        String ownerPrimaryBlogAddress) {
}
