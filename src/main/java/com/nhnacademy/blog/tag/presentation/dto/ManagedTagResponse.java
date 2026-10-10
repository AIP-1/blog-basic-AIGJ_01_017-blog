package com.nhnacademy.blog.tag.presentation.dto;

import com.nhnacademy.blog.tag.application.ManagedTag;

/** 관리 화면 태그 한 줄 `{ id, name, postCount, publishedCount }` (contracts/rest-api.md GET /api/manage/tags). */
public record ManagedTagResponse(Long id, String name, long postCount, long publishedCount) {

    public static ManagedTagResponse from(ManagedTag tag) {
        return new ManagedTagResponse(tag.id(), tag.name(), tag.postCount(), tag.publishedCount());
    }

}
