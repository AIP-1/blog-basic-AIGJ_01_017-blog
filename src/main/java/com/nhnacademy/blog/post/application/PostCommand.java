package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.post.domain.Topic;
import com.nhnacademy.blog.post.domain.Visibility;
import java.util.List;

/**
 * 글 발행·수정에 쓰는 값 (POST-01, POST-02). 형식 검증은 요청 DTO에서 이미 했다.
 *
 * @param categoryId null이면 미분류
 * @param topic      null이면 주제 없음
 * @param tagNames   정리 전 태그 이름. TagService가 정리하고 10개를 넘으면 400 TOO_MANY_TAGS
 */
public record PostCommand(String title, String contentHtml, Long categoryId, Topic topic, Visibility visibility,
                          List<String> tagNames) {
}
