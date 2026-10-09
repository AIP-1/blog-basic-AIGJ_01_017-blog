package com.nhnacademy.blog.recommend.application;

import com.nhnacademy.blog.recommend.config.RecommendProperties;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Ollama의 임베딩 API를 부른다 (R-02). POST {ollama}/api/embed { model, input } → { embeddings: [[...]] }.
 * bge-m3는 정규화된(길이 1) 1024차원 벡터를 준다.
 */
@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final String model;

    public OllamaEmbeddingClient(RecommendProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.ollamaUrl())
                .requestFactory(requestFactory)
                .build();
        this.model = properties.model();
    }

    @Override
    public float[] embed(String text) {
        EmbedResponse response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(model, text))
                .retrieve()
                .body(EmbedResponse.class);
        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new IllegalStateException("Ollama가 임베딩을 돌려주지 않았습니다");
        }
        List<Double> values = response.embeddings().getFirst();
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    record EmbedRequest(String model, String input) {
    }

    record EmbedResponse(List<List<Double>> embeddings) {
    }

}
