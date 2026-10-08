package com.nhnacademy.blog.global.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 연타 방지 키를 얼마나 기억할지 (R-09 짧은 TTL).
 */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(Duration ttl) {
}
