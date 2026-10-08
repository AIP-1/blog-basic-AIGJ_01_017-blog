package com.nhnacademy.blog.tag.application;

/** 태그 하나와 그 태그가 달린, 보는 사람이 볼 수 있는 글 수 (TAG-03). */
public record TagCount(Long id, String name, long postCount) {
}
