package com.nhnacademy.blog.bookmark.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nhnacademy.blog.bookmark.application.BookmarkService;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import java.time.OffsetDateTime;

/**
 * 저장한 글 한 줄. 볼 수 있으면 `{ post, savedAt, visible: true }`, 볼 수 없으면
 * `{ postId, visible: false, titleSnapshot, blogNameSnapshot, savedAt }`(contracts SOC-03). 없는 칸은 응답에서 뺀다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SavedPostResponse(Long postId, PostSummaryResponse post, boolean visible, String titleSnapshot,
                                String blogNameSnapshot, OffsetDateTime savedAt) {

    public static SavedPostResponse from(BookmarkService.SavedItem item, String thumbnailUrl) {
        OffsetDateTime savedAt = DateTimes.toOffset(item.bookmark().getCreatedAt());
        if (item.post() == null) {
            return new SavedPostResponse(item.bookmark().getPostId(), null, false, item.bookmark().getTitleSnapshot(),
                    item.bookmark().getBlogNameSnapshot(), savedAt);
        }
        return new SavedPostResponse(item.bookmark().getPostId(),
                PostSummaryResponse.of(item.post(), item.post().getBlog(), thumbnailUrl), true, null, null, savedAt);
    }

}
