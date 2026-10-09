package com.nhnacademy.blog.post.application;

/** 글이 지워졌다 (POST-03). 비슷한 글 추천(recommend)이 그 글의 임베딩을 지우는 데 쓴다. */
public record PostDeletedEvent(Long postId) {
}
