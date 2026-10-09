package com.nhnacademy.blog.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async}를 켠다. 비슷한 글 추천의 임베딩 만들기(T069b)가 글 저장 요청과 따로 돈다.
 * 실행할 스레드 풀은 Spring Boot가 만들어 주는 applicationTaskExecutor다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
