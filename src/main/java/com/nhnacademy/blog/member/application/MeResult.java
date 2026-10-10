package com.nhnacademy.blog.member.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;

/**
 * 내 정보 화면에 필요한 것. primaryBlog는 대표 블로그가 없으면 null이다.
 * profileImageUrl은 프로필 사진의 썸네일 주소이고, 없으면 null이다. unreadNotificationCount는 머리글의 알림 수(SUB-04).
 */
public record MeResult(Member member, Blog primaryBlog, String profileImageUrl, long unreadNotificationCount) {
}
