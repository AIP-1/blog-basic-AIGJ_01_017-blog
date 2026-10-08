package com.nhnacademy.blog.global.security;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * 목록에 보일 글 요약과 검색용 본문 글자를 만든다. 본문에서 태그를 모두 지운 글자만 남긴다 (R-05, R-16).
 */
@Component
public class SummaryExtractor {

    /** post.summary 컬럼 길이. */
    public static final int MAX_LENGTH = 300;

    private static final String ELLIPSIS = "…";

    /** 본문 전체 글자(검색용, SRCH-01). &amp;amp; 같은 문자 참조도 글자로 풀린다. */
    public String plainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().strip();
    }

    public String extract(String html) {
        String text = plainText(html);
        if (text.isEmpty()) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= MAX_LENGTH) {
            return text;
        }
        // 이모지처럼 두 글자(char)로 된 문자가 잘리지 않게 코드 포인트 기준으로 자른다
        int end = text.offsetByCodePoints(0, MAX_LENGTH - ELLIPSIS.length());
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }

}
