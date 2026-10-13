package com.nhnacademy.blog.global.web;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

/**
 * LIKE 검색어 만들기. 검색(SRCH-01, SRCH-02), 내 글 관리 검색(MNG-01), 관리자 회원 검색(ADMIN-02)이 같이 쓴다.
 * LIKE의 특수 문자(%, _)를 글자 그대로 찾게 한다. 안 하면 "100%"가 "100"으로 시작하는 모든 글에 걸린다.
 * 쿼리에서는 {@link #containsIgnoreCase}로 대소문자를 무시하고(PostgreSQL ILIKE) 이스케이프 문자를 알려 준다.
 */
public final class LikePatterns {

    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** 글자가 어디에든 들어 있는 것: %글자%. */
    public static String contains(String text) {
        return "%" + escape(text) + "%";
    }

    /**
     * 칸에 패턴이 들어 있는가, 대소문자 무시(ILIKE). PostgreSQL의 LIKE는 대소문자를 가리므로(MySQL ai_ci 정렬 규칙과 다르다)
     * 검색에는 ILIKE를 쓴다. 표준 JPA에는 없어서 Hibernate의 CriteriaBuilder로 부른다(Spring Data가 넘기는 cb가 그것이다).
     */
    public static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> column, String pattern) {
        return ((HibernateCriteriaBuilder) cb).ilike(column, pattern, ESCAPE);
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
