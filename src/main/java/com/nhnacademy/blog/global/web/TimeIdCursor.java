package com.nhnacademy.blog.global.web;

import com.nhnacademy.blog.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * (시각, id) 커서. 최신순 목록은 (published_at, id), 작성순 목록은 (created_at, id)를 넣는다.
 * 프론트는 해석하지 않고 그대로 돌려보내는 불투명 문자열이다.
 */
public record TimeIdCursor(LocalDateTime time, long id) {

    private static final String SEPARATOR = ",";

    public String encode() {
        String raw = time + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** null이면 처음부터라는 뜻이라 null을 돌려준다. 형식이 틀리면 400. */
    public static TimeIdCursor decode(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(SEPARATOR, -1);
            if (parts.length != 2) {
                throw invalid();
            }
            return new TimeIdCursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return BusinessException.invalidField("cursor", "목록 위치가 올바르지 않습니다. 처음부터 다시 불러와 주세요.");
    }

}
