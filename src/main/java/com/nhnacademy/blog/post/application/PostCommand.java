package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.post.domain.Topic;
import com.nhnacademy.blog.post.domain.Visibility;

/**
 * 글 발행·수정에 쓰는 값 (POST-01, POST-02). 형식 검증은 요청 DTO에서 이미 했다.
 *
 * @param categoryId null이면 미분류
 * @param topic      null이면 주제 없음
 */
public record PostCommand(String title, String contentHtml, Long categoryId, Topic topic, Visibility visibility) {
}
