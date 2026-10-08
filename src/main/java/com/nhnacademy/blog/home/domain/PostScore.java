package com.nhnacademy.blog.home.domain;

import java.io.Serializable;

/**
 * 글 하나의 인기 점수. Redis 캐시에 그대로 들어가므로(Java 직렬화) Serializable이다.
 */
public record PostScore(long postId, long score) implements Serializable {
}
