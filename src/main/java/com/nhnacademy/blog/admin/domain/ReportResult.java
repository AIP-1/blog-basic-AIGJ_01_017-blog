package com.nhnacademy.blog.admin.domain;

/** 신고 처리 결과 (ADMIN-04, ERD ck_report_result). */
public enum ReportResult {
    /** 글·댓글 숨김 */
    BLIND,
    /** 블로그 이용 제한 (블로그 신고, 또는 글 신고면 그 글의 블로그) */
    RESTRICT_BLOG,
    /** 작성자 정지 */
    SUSPEND,
    /** 기각 */
    REJECT
}
