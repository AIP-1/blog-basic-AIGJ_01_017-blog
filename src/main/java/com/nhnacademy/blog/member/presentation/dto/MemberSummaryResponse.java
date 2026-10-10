package com.nhnacademy.blog.member.presentation.dto;

import com.nhnacademy.blog.member.domain.Member;

/**
 * MemberSummary (contracts/rest-api.md 주요 응답 객체). 블로그 주인, 글쓴이, 댓글 작성자 등.
 * primaryBlogAddress는 대표 블로그가 없거나 볼 수 없으면 null이다(BLOG-08 닉네임 링크).
 * profileImageUrl은 회원 프로필 사진(AUTH-05)의 썸네일 주소, 없으면 null이다.
 */
public record MemberSummaryResponse(Long id, String nickname, String profileImageUrl, String primaryBlogAddress) {

    public static MemberSummaryResponse of(Member member, String primaryBlogAddress, String profileImageUrl) {
        return new MemberSummaryResponse(member.getId(), member.getNickname(), profileImageUrl, primaryBlogAddress);
    }

}
