package com.nhnacademy.blog.tag.application;

/**
 * 관리 화면의 태그 한 줄 (TAG-04). postCount는 지우지 않은 글 전부(임시저장·예약 포함),
 * publishedCount는 그 가운데 블로그 화면에 나오는 발행 글 수다(0이면 태그 주소에 글이 없다).
 */
public record ManagedTag(Long id, String name, long postCount, long publishedCount) {
}
