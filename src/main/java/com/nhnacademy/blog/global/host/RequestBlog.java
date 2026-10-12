package com.nhnacademy.blog.global.host;

/**
 * 요청이 가리키는 곳 (R-04). 블로그가 실제로 있는지는 아직 모른다.
 * API는 X-Blog-Address 헤더로, 화면 주소는 /@{address} 경로로 블로그를 가리킨다(2026-10-12 지원 결정).
 */
public sealed interface RequestBlog {

    /** 플랫폼(블로그를 가리키지 않음). */
    record Platform() implements RequestBlog {
    }

    /** 블로그 주소. address는 주소 규칙을 통과했다. */
    record BlogAddress(String address) implements RequestBlog {
    }

    /** 블로그를 가리켰지만 주소 규칙에 맞지 않거나 예약어다. 404다. */
    record Unknown() implements RequestBlog {
    }

}
