package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.post.domain.Post;
import java.time.OffsetDateTime;

/**
 * PostSummary (contracts/rest-api.md 주요 응답 객체). 목록 한 줄.
 * category가 null이면 미분류. thumbnailUrl은 본문 첫 이미지의 썸네일이고 없으면 null(PostThumbnails).
 */
public record PostSummaryResponse(Long id, String title, String summary, String thumbnailUrl, BlogRef blog,
                                  CategoryRef category, String topic, OffsetDateTime publishedAt, int likeCount,
                                  int commentCount) {

    public record BlogRef(Long id, String address, String name) {
    }

    public record CategoryRef(Long id, String name) {
    }

    /**
     * blog는 글이 속한 블로그다. 블로그 메인은 목록을 부른 블로그를 넘기고(post.getBlog()를 읽지 않음),
     * 홈은 블로그를 함께 읽어 온 post.getBlog()를 넘긴다.
     */
    public static PostSummaryResponse of(Post post, Blog blog, String thumbnailUrl) {
        Category category = post.getCategory();
        return new PostSummaryResponse(post.getId(), post.getTitle(), post.getSummary(), thumbnailUrl,
                new BlogRef(blog.getId(), blog.getAddress(), blog.getName()),
                category == null ? null : new CategoryRef(category.getId(), category.getName()),
                post.getTopic() == null ? null : post.getTopic().name(),
                DateTimes.toOffset(post.getPublishedAt()), post.getLikeCount(), post.getCommentCount());
    }

}
