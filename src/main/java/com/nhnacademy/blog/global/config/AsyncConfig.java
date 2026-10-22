package com.nhnacademy.blog.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async}를 켠다. 비슷한 글 추천의 임베딩 만들기(T069b)가 글 저장 요청과 따로 돈다.
 * 실행할 스레드 풀은 Spring Boot가 만들어 주는 applicationTaskExecutor다.
 * <p>
 * 앱이 꺼질 때(SIGTERM, docker stop) 돌고 있는 작업은 종료 단계 제한 시간(spring.lifecycle.timeout-per-shutdown-phase,
 * 기본 30초)까지 기다려 준다. DB 연결은 그 뒤에 닫히므로 작업이 끝까지 DB를 쓸 수 있다. 30초를 넘으면 DB를 닫고 작업을 끊는다.
 * spring.task.execution.shutdown.await-termination=true는 넣지 않는다. 켜면 종료 단계에서 기다리지 않고 DB부터 닫은 뒤에
 * 기다려서, 작업이 끝나도 DB를 쓰지 못한다. (2026-10-15 실험: 8초 작업은 기본값으로 끝까지 돌았고, 45초 작업은 30초에 끊겼고,
 * await-termination을 켜자 SIGTERM 바로 DB를 닫았다.)
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
