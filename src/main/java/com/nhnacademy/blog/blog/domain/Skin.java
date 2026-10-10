package com.nhnacademy.blog.blog.domain;

/**
 * 미리 만든 스킨 (BLOG-05, 목업 manage-settings "기본 · 매거진 · 노트"). 화면은 이름으로 모양(글꼴, 머리글, 목록 간격)을 고른다.
 * DB 칸(skin VARCHAR(20))에는 CHECK 제약이 없어 여기서 세 가지로 정한다.
 */
public enum Skin {
    BASIC,
    MAGAZINE,
    NOTE
}
