package com.nhnacademy.blog.global.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비밀번호·인증 코드 시도 제한 (R-17). window 안에 maxFailures번 틀리면 마지막으로 틀린 때부터 window 동안 막는다.
 * 대상(이메일·회원)별로 maxFailures, 같은 IP는 모든 대상을 합쳐 ipMaxFailures다.
 */
@ConfigurationProperties(prefix = "app.attempt-limit")
public record AttemptLimitProperties(int maxFailures, int ipMaxFailures, Duration window) {
}
