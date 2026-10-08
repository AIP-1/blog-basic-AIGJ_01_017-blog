package com.nhnacademy.blog.global.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 연타 방지 대상 API (R-09). Idempotency-Key 헤더(UUID)가 필수이고,
 * 같은 키로 다시 오면 처음 응답을 그대로 돌려준다. 글 발행·임시저장 생성, 댓글·방명록 작성에 붙인다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
