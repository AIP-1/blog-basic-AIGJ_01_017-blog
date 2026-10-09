package com.nhnacademy.blog.home.presentation.dto;

import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;

/** 홈 인기 글 `{ snapshotAt, items: [{ rank, post }] }` (contracts/rest-api.md GET /api/home/popular). */
public record PopularPostsResponse(OffsetDateTime snapshotAt, List<Item> items) {

    public record Item(int rank, PostSummaryResponse post) {
    }

}
