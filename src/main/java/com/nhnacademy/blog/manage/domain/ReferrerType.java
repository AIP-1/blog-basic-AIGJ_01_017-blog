package com.nhnacademy.blog.manage.domain;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 유입 종류 (MNG-03, ERD ck_blog_visit_referrer_type). 방문 요청의 Referer 헤더로 정한다.
 * 검색 엔진·SNS 목록은 이 서비스가 정한 기본값이다(목록에 없으면 기타).
 */
public enum ReferrerType {
    SEARCH,
    SNS,
    DIRECT,
    INTERNAL,
    OTHER;

    private static final List<String> SEARCH_HOSTS = List.of("google.", "search.naver.com", "search.daum.net",
            "bing.com", "duckduckgo.com", "search.yahoo.", "baidu.com");
    private static final List<String> SNS_HOSTS = List.of("x.com", "twitter.com", "t.co", "facebook.com",
            "instagram.com", "threads.net", "linkedin.com", "kakao.com", "band.us", "reddit.com");

    /** Referer로 정한 유입 종류와 호스트(100자까지, 직접 방문이면 빈 값). */
    public record Classified(ReferrerType type, String host) {
    }

    /**
     * referer가 없거나 읽을 수 없으면 직접(DIRECT), 플랫폼이나 그 아래 블로그 주소면 블로그 안(INTERNAL),
     * 검색 엔진·SNS 목록에 있으면 그 종류, 아니면 기타(OTHER).
     */
    public static Classified classify(String referer, String platform) {
        if (referer == null || referer.isBlank()) {
            return new Classified(DIRECT, "");
        }
        String host;
        try {
            host = URI.create(referer.strip()).getHost();
        } catch (IllegalArgumentException e) {
            return new Classified(DIRECT, "");
        }
        if (host == null || host.isBlank()) {
            return new Classified(DIRECT, "");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        if (host.length() > 100) {
            host = host.substring(0, 100);
        }
        String platformHost = platform.toLowerCase(Locale.ROOT);
        if (host.equals(platformHost) || host.endsWith("." + platformHost)) {
            return new Classified(INTERNAL, host);
        }
        if (matches(host, SEARCH_HOSTS)) {
            return new Classified(SEARCH, host);
        }
        if (matches(host, SNS_HOSTS)) {
            return new Classified(SNS, host);
        }
        return new Classified(OTHER, host);
    }

    private static boolean matches(String host, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern.endsWith(".")) {
                if (host.startsWith(pattern) || host.contains("." + pattern)) {
                    return true;
                }
            } else if (host.equals(pattern) || host.endsWith("." + pattern)) {
                return true;
            }
        }
        return false;
    }
}
