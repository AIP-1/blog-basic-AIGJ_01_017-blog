package com.nhnacademy.blog.ranking.application;

import com.nhnacademy.blog.ranking.domain.BlogScore;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 인기 블로거 순위 하나 (HOME-04). snapshotAt은 그 시간 칸의 시작(정시)이다. */
public record BloggerSnapshot(LocalDateTime snapshotAt, List<BlogScore> scores) implements Serializable {
}
