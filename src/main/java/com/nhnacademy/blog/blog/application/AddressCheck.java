package com.nhnacademy.blog.blog.application;

/**
 * 블로그 주소를 쓸 수 있는지 (BLOG-01). 쓸 수 없으면 reason에 이유가 있다.
 */
public record AddressCheck(boolean available, Reason reason) {

    public enum Reason {
        /** 영문 소문자·숫자·하이픈 4~32자가 아니거나 하이픈으로 시작·끝남. */
        INVALID,
        /** 예약어(www, api, admin 등). */
        RESERVED,
        /** 쓰는 중이거나 삭제된 블로그가 쓴 주소. */
        TAKEN
    }

    static AddressCheck ok() {
        return new AddressCheck(true, null);
    }

    static AddressCheck unavailable(Reason reason) {
        return new AddressCheck(false, reason);
    }

}
