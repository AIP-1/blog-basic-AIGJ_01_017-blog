package com.nhnacademy.blog;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * 로그인은 JWT 쿠키(AuthService, JwtAuthenticationFilter)로 하고 Spring Security의 아이디·비밀번호 로그인(httpBasic, formLogin)은 끈다.
 * 그래서 Spring Boot가 기본으로 만드는 메모리 계정(UserDetailsServiceAutoConfiguration)은 필요 없다. 두면 쓰지 않는 계정이 생기고
 * 그 임시 비밀번호가 시작 로그에 찍힌다(T073).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BlogApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(BlogApplication.class, args);
    }

}
