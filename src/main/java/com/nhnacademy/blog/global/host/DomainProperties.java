package com.nhnacademy.blog.global.host;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 플랫폼 주소 설정. 블로그 주소는 {address}.{platform}이고, 로그인 쿠키는 .{platform}에 둔다.
 */
@ConfigurationProperties(prefix = "app.domain")
public record DomainProperties(String platform) {

    /** 모든 블로그 주소가 함께 받는 쿠키 도메인. */
    public String cookieDomain() {
        return "." + platform;
    }

    public String blogHost(String address) {
        return address + "." + platform;
    }

}
