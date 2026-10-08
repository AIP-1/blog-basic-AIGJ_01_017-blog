package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.post.application.ManagedPost;
import com.nhnacademy.blog.post.domain.Post;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 편집용 글 (GET /api/manage/posts/{id}). 주인이 수정 화면을 열 때 쓴다.
 * blind는 관리자가 숨긴 글일 때 { reason, reasonMessage }, 아니면 null이다.
 */
public record ManagedPostResponse(Long id, String title, String contentHtml, Long categoryId, List<String> tagNames,
                                  String topic, String visibility, String status, OffsetDateTime publishedAt,
                                  OffsetDateTime updatedAt, boolean commentAllowed, Map<String, String> blind) {

    /** post.getCategory()는 지연 로딩 프록시라 id만 읽는다(초기화 없이 읽힌다). */
    public static ManagedPostResponse from(ManagedPost managed) {
        Post post = managed.post();
        return new ManagedPostResponse(post.getId(), post.getTitle(), post.getContentHtml(),
                post.getCategory() == null ? null : post.getCategory().getId(), managed.tagNames(),
                post.getTopic() == null ? null : post.getTopic().name(), post.getVisibility().name(),
                post.getStatus().name(), DateTimes.toOffset(post.getPublishedAt()),
                DateTimes.toOffset(post.getUpdatedAt()), post.isCommentAllowed(), managed.blind());
    }

}
