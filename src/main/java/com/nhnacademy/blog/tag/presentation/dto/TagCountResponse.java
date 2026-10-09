package com.nhnacademy.blog.tag.presentation.dto;

import com.nhnacademy.blog.tag.application.TagCount;

/** 태그 목록 한 줄 `{ id, name, postCount }` (contracts/rest-api.md GET /api/tags, 사이드바 TAG 모듈). */
public record TagCountResponse(Long id, String name, long postCount) {

    public static TagCountResponse from(TagCount tag) {
        return new TagCountResponse(tag.id(), tag.name(), tag.postCount());
    }

}
