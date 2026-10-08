package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.post.domain.Post;
import java.time.OffsetDateTime;

/**
 * PostSummary (contracts/rest-api.md 주요 응답 객체). 목록 한 줄.
 * category가 null이면 미분류. 썸네일은 이미지 기능(스텝 7)에서 채운다.
 */
public record PostSummaryResponse(Long id, String title, String summary, String thumbnailUrl, BlogRef blog,
                                  CategoryRef category, String topic, OffsetDateTime publishedAt, int likeCount,
                                  int commentCount) {

    public record BlogRef(Long id, String address, String name) {
    }

    public record CategoryRef(Long id, String name) {
    }

    /** blog는 목록을 부른 블로그다. post.getBlog()는 지연 로딩이라 읽지 않는다. */
    public static PostSummaryResponse of(Post post, Blog blog) {
        Category category = post.getCategory();
        return new PostSummaryResponse(post.getId(), post.getTitle(), post.getSummary(), null,
                new BlogRef(blog.getId(), blog.getAddress(), blog.getName()),
                category == null ? null : new CategoryRef(category.getId(), category.getName()),
                post.getTopic() == null ? null : post.getTopic().name(),
                DateTimes.toOffset(post.getPublishedAt()), post.getLikeCount(), post.getCommentCount());
    }

}
