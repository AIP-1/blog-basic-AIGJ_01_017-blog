package com.nhnacademy.blog.comment.presentation.dto;

import java.util.List;

/** 댓글 더보기 응답. totalCount는 지우지 않은 댓글 전체 수, 끝이면 nextCursor가 null이다. */
public record CommentListResponse(List<CommentResponse> content, String nextCursor, long totalCount) {
}
