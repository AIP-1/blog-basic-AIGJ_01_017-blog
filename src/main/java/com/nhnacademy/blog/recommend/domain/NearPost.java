package com.nhnacademy.blog.recommend.domain;

import com.nhnacademy.blog.post.domain.Post;

/**
 * 임베딩이 가까운 글 하나 (의미 검색 T121, 비슷한 글 T069c).
 *
 * @param post       글
 * @param similarity 코사인 유사도(1 - 코사인 거리). 1에 가까울수록 비슷하다
 */
public record NearPost(Post post, double similarity) {
}
