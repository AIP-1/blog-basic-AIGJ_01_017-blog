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
 * thumbnailImageId는 주인이 고른 대표 이미지(없으면 null = 본문 첫 이미지), images는 본문에 든 이미지(본문 순서)로
 * 수정 화면이 대표 이미지 후보로 보여 준다(POST-07).
 */
public record ManagedPostResponse(Long id, String title, String contentHtml, Long categoryId, List<String> tagNames,
                                  String topic, String visibility, String status, OffsetDateTime publishedAt,
                                  OffsetDateTime updatedAt, boolean commentAllowed, Map<String, String> blind,
                                  Long thumbnailImageId, List<BodyImage> images) {

    /** 본문에 든 이미지 { id, url, thumbnailUrl } (POST /api/images 응답과 같은 모양). */
    public record BodyImage(Long id, String url, String thumbnailUrl) {
    }

    /** post.getCategory()는 지연 로딩 프록시라 id만 읽는다(초기화 없이 읽힌다). */
    public static ManagedPostResponse from(ManagedPost managed) {
        Post post = managed.post();
        return new ManagedPostResponse(post.getId(), post.getTitle(), post.getContentHtml(),
                post.getCategory() == null ? null : post.getCategory().getId(), managed.tagNames(),
                post.getTopic() == null ? null : post.getTopic().name(), post.getVisibility().name(),
                post.getStatus().name(), DateTimes.toOffset(post.getPublishedAt()),
                DateTimes.toOffset(post.getUpdatedAt()), post.isCommentAllowed(), managed.blind(),
                post.getThumbnailImageId(),
                managed.images().stream()
                        .map(image -> new BodyImage(image.getId(), image.getPath(), image.getThumbnailPath()))
                        .toList());
    }

}
