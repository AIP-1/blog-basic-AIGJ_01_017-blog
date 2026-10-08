package com.nhnacademy.blog.global.host;

/**
 * 요청 Host가 가리키는 곳. 블로그가 실제로 있는지는 아직 모른다.
 */
public sealed interface RequestHost {

    /** 플랫폼 주소 (blog.com, www.blog.com). */
    record Platform() implements RequestHost {
    }

    /** 블로그 주소 ({address}.blog.com). address는 주소 규칙을 통과했다. */
    record BlogAddress(String address) implements RequestHost {
    }

    /** 이 서비스 주소가 아니거나, 주소 규칙에 맞지 않거나, 예약어다. 블로그 주소라면 404다. */
    record Unknown() implements RequestHost {
    }

}
