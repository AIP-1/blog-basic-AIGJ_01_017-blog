package com.nhnacademy.blog.post.application;

/**
 * 글이 발행되거나 내용이 고쳐졌다 (POST-01, POST-02). 비슷한 글 추천(recommend)이 임베딩을 다시 만드는 데 쓴다.
 * 글 쓰기 쪽은 누가 이 이벤트를 받는지 모른다(post가 recommend에 기대지 않게).
 */
public record PostContentChangedEvent(Long postId) {
}
