package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.recommend.application.EmbeddingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 검색어를 임베딩으로 바꾼다 (T121). 글과 같은 모델(EmbeddingClient)이어야 거리를 잴 수 있다.
 * 다음 페이지를 누를 때마다 같은 검색어를 다시 계산하지 않게 Redis 캐시(5분)에 둔다. 실패(예외)는 캐시하지 않는다.
 * <p>
 * 캐시는 프록시가 거는 것이라, 같은 클래스 안에서 부르면 걸리지 않는다. 그래서 SemanticSearchService와 따로 둔다.
 * 추천을 꺼 두면(app.recommend.enabled=false) 임베딩 서버가 없어 이 부품도 없다.
 */
@ConditionalOnProperty(name = "app.recommend.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class QueryEmbeddings {

    public static final String CACHE = "semanticQuery";

    private final EmbeddingClient embeddingClient;

    public QueryEmbeddings(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Cacheable(cacheNames = CACHE, key = "#query")
    public float[] embed(String query) {
        return embeddingClient.embed(query);
    }

}
