package com.nhnacademy.blog.category.presentation.dto;

import com.nhnacademy.blog.category.domain.Category;

/** 카테고리 추가 응답 (201). parentId는 최상위면 null이다. */
public record CategoryCreatedResponse(Long id, String name, Long parentId, int sortOrder) {

    public static CategoryCreatedResponse from(Category category) {
        return new CategoryCreatedResponse(category.getId(), category.getName(),
                category.isChild() ? category.getParent().getId() : null, category.getSortOrder());
    }

}
