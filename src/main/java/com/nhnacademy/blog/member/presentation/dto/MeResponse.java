package com.nhnacademy.blog.member.presentation.dto;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.member.application.MeResult;
import com.nhnacademy.blog.member.domain.Member;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Me (contracts/rest-api.md 주요 응답 객체). GET /api/me, 가입, 로그인이 같이 쓴다.
 * email은 소셜로 가입한 회원이면 null, hasPassword는 이메일 가입 회원만 true, socialAccounts는 연결된 소셜 계정(T100).
 */
public record MeResponse(Long id, String email, String nickname, String profileImageUrl, String role,
                         boolean hasPassword, BlogRef primaryBlog, List<SocialAccount> socialAccounts,
                         long unreadNotificationCount) {

    public record BlogRef(Long id, String address, String name) {

        static BlogRef from(Blog blog) {
            return blog == null ? null : new BlogRef(blog.getId(), blog.getAddress(), blog.getName());
        }

    }

    public record SocialAccount(String provider, OffsetDateTime linkedAt) {
    }

    public static MeResponse from(MeResult result) {
        Member member = result.member();
        return new MeResponse(member.getId(), member.getEmail(), member.getNickname(), result.profileImageUrl(),
                member.getRole().name(), member.getPasswordHash() != null, BlogRef.from(result.primaryBlog()),
                result.socialAccounts().stream()
                        .map(account -> new SocialAccount(account.provider(), DateTimes.toOffset(account.linkedAt())))
                        .toList(),
                result.unreadNotificationCount());
    }

}
