package com.nhnacademy.blog.tag.presentation.dto;

/** 태그 이름 바꾸기 `{ name }` (TAG-04). 이름 규칙은 글에 달 때와 같다(TagNames.normalizeOne). */
public record TagRenameRequest(String name) {
}
