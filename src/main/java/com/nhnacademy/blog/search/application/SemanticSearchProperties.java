package com.nhnacademy.blog.search.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 의미 검색 설정 (T121, OWN-07, R-02).
 *
 * @param minSimilarity 이보다 코사인 유사도가 낮은 글은 결과에서 뺀다. bge-m3로 잰 값: 관련 있는 글 0.42~0.58,
 *                      관련 없는 글 0.21~0.39(2026-10-13)
 * @param maxResults    가까운 순으로 이만큼까지만 보고 그 안에서 페이지를 나눈다. 의미 검색은 앞쪽 결과만 쓸모가 있다
 */
@ConfigurationProperties(prefix = "app.search.semantic")
public record SemanticSearchProperties(double minSimilarity, int maxResults) {
}
