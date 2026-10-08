package com.nhnacademy.blog.global.web;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 응답 시각은 한국 시간 ISO-8601(2026-10-08T13:20:00+09:00)이다 (contracts/rest-api.md 요청·응답 형식).
 * DB의 DATETIME은 시간대가 없는 LocalDateTime이라 한국 시간으로 붙여 OffsetDateTime으로 바꾼다.
 */
public final class DateTimes {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private DateTimes() {
    }

    public static OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(KST).toOffsetDateTime();
    }

}
