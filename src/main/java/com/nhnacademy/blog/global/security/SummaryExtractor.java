package com.nhnacademy.blog.global.security;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * 목록에 보일 글 요약을 만든다. 본문에서 태그를 모두 지운 글자만 남긴다 (R-05).
 */
@Component
public class SummaryExtractor {

    /** post.summary 컬럼 길이. */
    public static final int MAX_LENGTH = 300;

    private static final String ELLIPSIS = "…";

    public String extract(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(html).text().strip();
        if (text.codePointCount(0, text.length()) <= MAX_LENGTH) {
            return text;
        }
        // 이모지처럼 두 글자(char)로 된 문자가 잘리지 않게 코드 포인트 기준으로 자른다
        int end = text.offsetByCodePoints(0, MAX_LENGTH - ELLIPSIS.length());
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }

}
