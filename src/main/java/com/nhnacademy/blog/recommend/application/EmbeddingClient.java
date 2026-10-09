package com.nhnacademy.blog.recommend.application;

/**
 * 글자를 임베딩(숫자 벡터)으로 바꾼다 (R-02). 지금은 Ollama + bge-m3(OllamaEmbeddingClient).
 * 테스트는 모델 없이 같은 낱말이면 가까워지는 가짜를 쓴다.
 */
public interface EmbeddingClient {

    /** 길이가 1인(정규화된) 벡터를 돌려준다. 실패하면 예외. */
    float[] embed(String text);

}
