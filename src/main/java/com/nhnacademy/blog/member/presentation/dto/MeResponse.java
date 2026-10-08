package com.nhnacademy.blog.member.presentation.dto;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.application.MeResult;
import com.nhnacademy.blog.member.domain.Member;
import java.util.List;

/**
 * Me (contracts/rest-api.md 주요 응답 객체). GET /api/me, 가입, 로그인이 같이 쓴다.
 * 프로필 이미지(스텝 7), 소셜 연동(백로그), 알림(백로그)은 기능이 생기면 채운다.
 */
public record MeResponse(Long id, String email, String nickname, String profileImageUrl, String role,
                         boolean hasPassword, BlogRef primaryBlog, List<SocialAccount> socialAccounts,
                         long unreadNotificationCount) {

    public record BlogRef(Long id, String address, String name) {

        static BlogRef from(Blog blog) {
            return blog == null ? null : new BlogRef(blog.getId(), blog.getAddress(), blog.getName());
        }

    }

    public record SocialAccount(String provider, String linkedAt) {
    }

    public static MeResponse from(MeResult result) {
        Member member = result.member();
        return new MeResponse(member.getId(), member.getEmail(), member.getNickname(), null,
                member.getRole().name(), member.getPasswordHash() != null, BlogRef.from(result.primaryBlog()),
                List.of(), 0);
    }

}
