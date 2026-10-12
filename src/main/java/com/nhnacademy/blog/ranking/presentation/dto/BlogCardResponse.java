package com.nhnacademy.blog.ranking.presentation.dto;

import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;

/**
 * 순위·추천의 블로그 한 칸 `{ rank, blog, owner, subscriberCount, recentPostTitle }`.
 * recentPostTitle은 추천 블로그에서만(그 블로그의 최근 공개 글 제목), 다른 곳은 null이다.
 */
public record BlogCardResponse(int rank, BlogRef blog, MemberSummaryResponse owner, long subscriberCount,
                               String recentPostTitle) {

    public record BlogRef(Long id, String address, String name, String description, String profileImageUrl) {
    }

}
