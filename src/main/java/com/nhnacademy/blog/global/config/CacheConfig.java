package com.nhnacademy.blog.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache 저장소는 Redis. 저장소 종류와 TTL(5분)은 application.yml의 spring.cache 설정을 따른다 (R-02).
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
