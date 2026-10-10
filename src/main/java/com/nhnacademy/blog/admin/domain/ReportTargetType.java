package com.nhnacademy.blog.admin.domain;

/** 신고 대상 (ADMIN-04, ERD ck_report_target_type). */
public enum ReportTargetType {
    POST,
    COMMENT,
    BLOG;

    /** 관리 이력에 남길 때의 대상 종류. 이름이 같다. */
    public ModerationTargetType toModerationTarget() {
        return ModerationTargetType.valueOf(name());
    }
}
