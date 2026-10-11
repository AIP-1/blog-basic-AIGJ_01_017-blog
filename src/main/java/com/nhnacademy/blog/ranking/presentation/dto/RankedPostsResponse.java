package com.nhnacademy.blog.ranking.presentation.dto;

import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;

/** 랭킹 전체보기 인기 글 `{ snapshotAt, items: [{ rank, post }], nextOffset }`. */
public record RankedPostsResponse(OffsetDateTime snapshotAt, List<Item> items, Integer nextOffset) {

    public record Item(int rank, PostSummaryResponse post) {
    }

}
