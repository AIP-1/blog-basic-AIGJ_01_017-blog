package com.nhnacademy.blog.blog.presentation.dto;

/** 사이드바 모듈 한 칸 `{ moduleType, isVisible }` (GET·PUT /api/blog/sidebar/modules). */
public record SidebarModuleItem(String moduleType, Boolean isVisible) {
}
