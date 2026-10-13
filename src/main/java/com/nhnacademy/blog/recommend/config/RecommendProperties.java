package com.nhnacademy.blog.recommend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비슷한 글 추천·의미 검색의 임베딩 서버 설정 (OWN-06, OWN-07, R-02). 임베딩은 주 DB(PostgreSQL + pgvector)의 post_embedding에 둔다.
 *
 * @param ollamaUrl Ollama 주소. 예: http://localhost:11434
 * @param model     임베딩 모델 이름. bge-m3는 1024차원이다(표의 vector(1024)와 맞아야 한다)
 * @param timeout   임베딩 요청 하나를 기다리는 시간
 */
@ConfigurationProperties(prefix = "app.recommend")
public record RecommendProperties(String ollamaUrl, String model, Duration timeout) {
}
