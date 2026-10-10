package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.global.error.BusinessException;
import java.util.Arrays;

/**
 * 제재 사유 (contracts 제재 사유). 관리자는 사유를 직접 쓰지 않고 목록에서 고르고, 기타(ETC)만 설명을 쓴다(200자).
 */
public record Sanction(SanctionReason reason, String reasonDetail) {

    public static final int DETAIL_LENGTH = 200;

    /** 요청 값을 읽는다. 사유가 없거나 모르는 값이면 400(reason), 기타인데 설명이 없거나 길면 400(reasonDetail). */
    public static Sanction of(String reason, String reasonDetail) {
        SanctionReason parsed = parseReason("reason", reason);
        String detail = reasonDetail == null || reasonDetail.isBlank() ? null : reasonDetail.strip();
        if (parsed == SanctionReason.ETC && detail == null) {
            throw BusinessException.invalidField("reasonDetail", "기타를 고르면 설명을 써 주세요.");
        }
        if (detail != null && detail.length() > DETAIL_LENGTH) {
            throw BusinessException.invalidField("reasonDetail", "설명은 " + DETAIL_LENGTH + "자까지입니다.");
        }
        return new Sanction(parsed, detail);
    }

    static SanctionReason parseReason(String field, String value) {
        if (value == null || value.isBlank()) {
            throw BusinessException.invalidField(field, "사유를 골라 주세요.");
        }
        return Arrays.stream(SanctionReason.values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "알 수 없는 사유입니다."));
    }

    /** 알림·안내에 넣을 사유 이름. 기타면 설명까지. */
    public String label() {
        return reason == SanctionReason.ETC ? reason.getMessage() + ": " + reasonDetail : reason.getMessage();
    }

}
