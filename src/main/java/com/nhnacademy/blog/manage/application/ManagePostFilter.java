package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Visibility;

/**
 * 내 글 관리 목록의 거르기 조건 (MNG-01). null인 조건은 걸지 않는다.
 * categoryId가 0이면 미분류, 그 밖에는 그 카테고리와 하위 카테고리의 글. q는 제목에서 찾는다.
 */
public record ManagePostFilter(PostStatus status, Visibility visibility, Long categoryId, String q) {
}
