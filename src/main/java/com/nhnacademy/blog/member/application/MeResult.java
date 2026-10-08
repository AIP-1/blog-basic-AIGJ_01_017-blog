package com.nhnacademy.blog.member.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;

/**
 * 내 정보 화면에 필요한 것. primaryBlog는 대표 블로그가 없으면 null이다.
 */
public record MeResult(Member member, Blog primaryBlog) {
}
