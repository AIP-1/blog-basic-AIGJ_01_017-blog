package com.nhnacademy.blog.ranking.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** `{ snapshotAt, items, nextOffset }`. nextOffset이 null이면 끝(랭킹 전체보기의 더보기). */
public record RankedBlogsResponse(OffsetDateTime snapshotAt, List<BlogCardResponse> items, Integer nextOffset) {
}
