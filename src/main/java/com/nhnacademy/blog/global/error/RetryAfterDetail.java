package com.nhnacademy.blog.global.error;

/**
 * 429 TOO_MANY_REQUESTS의 detail. 몇 초 뒤에 다시 시도할 수 있는지 알려 준다.
 */
public record RetryAfterDetail(long retryAfterSeconds) {
}
