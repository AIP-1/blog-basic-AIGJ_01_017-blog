package com.nhnacademy.blog.member.presentation.dto;

import com.nhnacademy.blog.member.domain.Member;

/**
 * MemberSummary (contracts/rest-api.md 주요 응답 객체). 블로그 주인, 댓글 작성자 등.
 * primaryBlogAddress는 대표 블로그가 없거나 볼 수 없으면 null이다. 프로필 이미지는 스텝 7에서 채운다.
 */
public record MemberSummaryResponse(Long id, String nickname, String profileImageUrl, String primaryBlogAddress) {

    public static MemberSummaryResponse of(Member member, String primaryBlogAddress) {
        return new MemberSummaryResponse(member.getId(), member.getNickname(), null, primaryBlogAddress);
    }

}
