package com.nhnacademy.blog.blog.presentation.dto;

import java.util.List;

/** 글 옮기기 `{ postIds, targetBlogId }` (BLOG-06). */
public record MovePostsRequest(List<Long> postIds, Long targetBlogId) {
}
