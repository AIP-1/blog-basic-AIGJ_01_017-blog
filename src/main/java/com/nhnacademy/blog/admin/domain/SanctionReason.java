package com.nhnacademy.blog.admin.domain;

/**
 * 제재·신고 사유 (contracts/rest-api.md 제재 사유). 안내 문구는 DB가 아니라 여기에 둔다.
 */
public enum SanctionReason {

    SPAM("스팸·광고"),
    ADULT("음란·유해"),
    ABUSE("욕설·비방"),
    COPYRIGHT("저작권 침해"),
    ETC("기타");

    private final String message;

    SanctionReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
