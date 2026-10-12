package com.nhnacademy.blog.global.host;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 플랫폼 주소 설정. 블로그 주소는 {platform}/@{address} 경로다(스텝 21, 2026-10-12 지원 결정).
 * 로그인·방문 쿠키는 이 주소 하나에서만 쓰므로 Domain을 따로 두지 않는다.
 */
@ConfigurationProperties(prefix = "app.domain")
public record DomainProperties(String platform) {
}
