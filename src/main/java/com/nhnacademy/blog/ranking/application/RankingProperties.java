package com.nhnacademy.blog.ranking.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 블로그 점수 설정 (HOME-04, R-13). 조회·공감·댓글 가중치는 인기 글(app.popular)과 같고, 새 구독자 가중치와 기간만 따로다.
 * 기본값: 7일, 새 구독자 ×10 (Claude 기본값, review B).
 */
@ConfigurationProperties(prefix = "app.ranking")
public record RankingProperties(Duration blogWindow, int subscribeWeight) {
}
