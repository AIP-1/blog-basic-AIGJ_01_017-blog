package com.nhnacademy.blog.home.application;

import com.nhnacademy.blog.home.domain.PostScore;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 번 계산한 인기 순위 (HOME-02). snapshotAt은 계산한 시각이고, 화면에 "10:35 기준"으로 보인다.
 * 캐시에는 글 내용이 아니라 글 번호와 점수만 둔다. 5분 사이에 글이 지워지거나 비공개가 되어도
 * 읽을 때 가시성을 다시 확인해 빼기 위해서다.
 */
public record PopularSnapshot(LocalDateTime snapshotAt, List<PostScore> scores) implements Serializable {
}
