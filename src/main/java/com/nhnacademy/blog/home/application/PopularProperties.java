package com.nhnacademy.blog.home.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인기 점수 설정 (R-12, HOME-02). 최근 window 동안의 조회×viewWeight + 공감×likeWeight + 댓글×commentWeight.
 * 가중치는 Claude가 정한 기본값이라(review B) 코드를 고치지 않고 application.yml에서 바꾼다.
 */
@ConfigurationProperties(prefix = "app.popular")
public record PopularProperties(Duration window, int viewWeight, int likeWeight, int commentWeight) {
}
