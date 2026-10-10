package com.nhnacademy.blog.blog.domain;

/**
 * 사이드바 모듈 8종 (BLOG-04, BLOG-05, ERD ck_blog_sidebar_module_module_type). 선언 순서가 새 블로그의 기본 순서이고,
 * 새 블로그에서 방문자 수·인기 글·구독은 숨김으로 시작한다. 블로그 홈 바로가기(PROFILE)는 숨길 수 없다.
 */
public enum SidebarModuleType {
    PROFILE(true),
    CATEGORY(true),
    TAG(true),
    RECENT_POST(true),
    RECENT_COMMENT(true),
    VISITOR(false),
    POPULAR_POST(false),
    SUBSCRIBE(false);

    private final boolean visibleByDefault;

    SidebarModuleType(boolean visibleByDefault) {
        this.visibleByDefault = visibleByDefault;
    }

    public boolean isVisibleByDefault() {
        return visibleByDefault;
    }
}
