package com.nhnacademy.blog;

import com.nhnacademy.blog.recommend.application.EmbeddingClient;
import java.util.Locale;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 가짜 임베딩. 진짜 모델(Ollama) 없이, 같은 낱말이 많이 겹칠수록 가까워지는 1024차원 벡터를 만든다.
 * 낱말마다 해시로 칸 하나를 골라 1을 더하고 길이 1로 맞춘다(낱말 주머니). 결과가 늘 같아 테스트가 흔들리지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestRecommendConfiguration {

    public static final int DIMENSIONS = 1024;

    @Bean
    @Primary
    EmbeddingClient fakeEmbeddingClient() {
        return TestRecommendConfiguration::bagOfWords;
    }

    public static float[] bagOfWords(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
            }
        }
        double length = 0;
        for (float value : vector) {
            length += value * value;
        }
        if (length == 0) {
            vector[0] = 1;
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / Math.sqrt(length));
        }
        return vector;
    }

}
