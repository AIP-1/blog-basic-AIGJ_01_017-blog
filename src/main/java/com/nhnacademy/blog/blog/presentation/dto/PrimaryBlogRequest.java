package com.nhnacademy.blog.blog.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** 대표 블로그 바꾸기 { blogId } (PUT /api/me/primary-blog, BLOG-08). */
public record PrimaryBlogRequest(@NotNull(message = "블로그를 골라 주세요.") Long blogId) {
}
