package com.nhnacademy.blog.ranking.domain;

import java.io.Serializable;

/** 블로그 하나의 점수 (HOME-04). Redis 캐시에 담기므로 Serializable이다. */
public record BlogScore(Long blogId, long score) implements Serializable {
}
