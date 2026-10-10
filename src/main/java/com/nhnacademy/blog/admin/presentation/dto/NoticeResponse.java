package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.domain.Notice;
import com.nhnacademy.blog.global.web.DateTimes;
import java.time.OffsetDateTime;

/** 공지 상세 `{ id, title, content, createdAt, updatedAt }`. content는 글자 그대로(HTML 아님). */
public record NoticeResponse(Long id, String title, String content, OffsetDateTime createdAt,
                             OffsetDateTime updatedAt) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(),
                DateTimes.toOffset(notice.getCreatedAt()), DateTimes.toOffset(notice.getUpdatedAt()));
    }

}
