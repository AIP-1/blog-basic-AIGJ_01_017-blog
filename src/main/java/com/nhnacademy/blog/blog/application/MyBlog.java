package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;

/** 마이페이지 내 블로그 한 줄 (BLOG-08). postCount는 지우지 않은 발행 글 수. */
public record MyBlog(Blog blog, long postCount) {
}
