package com.nhnacademy.blog.recommend.domain;

/**
 * 검색어와 가까운 글 하나 (T121, OWN-07).
 *
 * @param postId     글 번호(MySQL post.id)
 * @param similarity 코사인 유사도(1 - 코사인 거리). 1에 가까울수록 비슷하다
 */
public record NearPost(long postId, double similarity) {
}
