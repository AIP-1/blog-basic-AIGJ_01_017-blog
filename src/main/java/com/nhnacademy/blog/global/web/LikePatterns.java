package com.nhnacademy.blog.global.web;

/**
 * LIKE 검색어 만들기. 블로그 안 검색(SRCH-01)과 내 글 관리 검색(MNG-01)이 같이 쓴다.
 * LIKE의 특수 문자(%, _)를 글자 그대로 찾게 한다. 안 하면 "100%"가 "100"으로 시작하는 모든 글에 걸린다.
 * 쿼리에서는 cb.like(칸, 패턴, ESCAPE)로 이스케이프 문자를 알려 준다.
 */
public final class LikePatterns {

    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** 글자가 어디에든 들어 있는 것: %글자%. */
    public static String contains(String text) {
        return "%" + escape(text) + "%";
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
