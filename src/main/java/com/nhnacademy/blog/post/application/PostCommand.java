package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Topic;
import com.nhnacademy.blog.post.domain.Visibility;
import java.util.List;

/**
 * 글 발행·임시저장·수정에 쓰는 값 (POST-01, POST-02, POST-08). 형식 검증은 요청 DTO에서 이미 했다.
 *
 * @param title            앞뒤 공백을 뗀 제목. 임시저장이면 빈 문자열일 수 있다
 * @param categoryId       null이면 미분류
 * @param topic            null이면 주제 없음
 * @param tagNames         정리 전 태그 이름. TagService가 정리하고 10개를 넘으면 400 TOO_MANY_TAGS
 * @param status           PUBLISHED 또는 DRAFT (SCHEDULED는 요청 DTO가 막았다)
 * @param thumbnailImageId 대표 이미지. 본문에 든 이미지여야 하고, null이면 본문 첫 이미지 (POST-07)
 */
public record PostCommand(String title, String contentHtml, Long categoryId, Topic topic, Visibility visibility,
                          List<String> tagNames, PostStatus status, Long thumbnailImageId) {
}
