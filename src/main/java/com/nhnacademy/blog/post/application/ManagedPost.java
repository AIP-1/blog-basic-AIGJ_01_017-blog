package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.post.domain.Post;
import java.util.List;
import java.util.Map;

/** 편집용 글 (수정 화면). 태그 이름은 트랜잭션 안에서 꺼내 둔다. blind는 숨긴 글일 때만 사유. */
public record ManagedPost(Post post, List<String> tagNames, Map<String, String> blind) {
}
