package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.global.error.BusinessException;
import java.time.LocalDateTime;

/** 정지 기간 (ADMIN-02). 요청 값은 7D, 30D, PERMANENT. */
public enum SuspensionPeriod {
    DAYS_7("7D", 7),
    DAYS_30("30D", 30),
    PERMANENT("PERMANENT", 0);

    private final String code;
    private final int days;

    SuspensionPeriod(String code, int days) {
        this.code = code;
        this.days = days;
    }

    /** 없거나 모르는 값이면 400(period). */
    public static SuspensionPeriod of(String code) {
        for (SuspensionPeriod period : values()) {
            if (period.code.equals(code)) {
                return period;
            }
        }
        throw BusinessException.invalidField("period", "정지 기간은 7D, 30D, PERMANENT 중 하나입니다.");
    }

    /** 정지가 끝나는 시각. 영구면 null. */
    public LocalDateTime until(LocalDateTime now) {
        return this == PERMANENT ? null : now.plusDays(days);
    }

}
