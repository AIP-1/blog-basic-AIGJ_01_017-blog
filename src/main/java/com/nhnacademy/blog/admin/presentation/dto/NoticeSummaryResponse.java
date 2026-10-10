package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.domain.Notice;
import com.nhnacademy.blog.global.web.DateTimes;
import java.time.OffsetDateTime;

/** 공지 목록 한 줄과 홈 상단 최신 공지 `{ id, title, createdAt }`. */
public record NoticeSummaryResponse(Long id, String title, OffsetDateTime createdAt) {

    public static NoticeSummaryResponse from(Notice notice) {
        return new NoticeSummaryResponse(notice.getId(), notice.getTitle(), DateTimes.toOffset(notice.getCreatedAt()));
    }

}
