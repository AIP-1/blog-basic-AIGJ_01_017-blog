package com.nhnacademy.blog.manage.presentation.dto;

import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 내 글 관리 목록 한 줄 (contracts PostSummary + 주인 목록 칸). PostSummary에 status, visibility, scheduledAt, blinded가 더 붙고,
 * 숨긴 글이면 blind에 사유 { reason, reasonMessage }가 있다. updatedAt은 임시저장 글의 날짜로 쓴다.
 */
public record ManagedPostSummaryResponse(Long id, String title, String summary, String thumbnailUrl,
                                         PostSummaryResponse.CategoryRef category, String topic,
                                         OffsetDateTime publishedAt, OffsetDateTime updatedAt, int likeCount,
                                         int commentCount, long viewCount, String status, String visibility,
                                         OffsetDateTime scheduledAt, boolean blinded, Map<String, String> blind) {

    public static ManagedPostSummaryResponse of(Post post, String thumbnailUrl, Map<String, String> blind) {
        Category category = post.getCategory();
        return new ManagedPostSummaryResponse(post.getId(), post.getTitle(), post.getSummary(), thumbnailUrl,
                category == null ? null : new PostSummaryResponse.CategoryRef(category.getId(), category.getName()),
                post.getTopic() == null ? null : post.getTopic().name(),
                DateTimes.toOffset(post.getPublishedAt()), DateTimes.toOffset(post.getUpdatedAt()),
                post.getLikeCount(), post.getCommentCount(), post.getViewCount(), post.getStatus().name(),
                post.getVisibility().name(), DateTimes.toOffset(post.getScheduledAt()), post.isBlinded(), blind);
    }

}
