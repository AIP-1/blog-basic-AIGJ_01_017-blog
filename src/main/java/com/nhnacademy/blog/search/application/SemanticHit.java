package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.post.domain.Post;

/**
 * 의미 검색 결과 한 줄 (T121).
 *
 * @param post       찾은 글(볼 수 있는 글만)
 * @param similarity 검색어와의 코사인 유사도. 1에 가까울수록 뜻이 가깝다
 */
public record SemanticHit(Post post, double similarity) {
}
