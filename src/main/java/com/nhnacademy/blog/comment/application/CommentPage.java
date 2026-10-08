package com.nhnacademy.blog.comment.application;

import java.util.List;

/**
 * 댓글 더보기 한 묶음. fetched는 다음 묶음이 있는지 보려고 하나 더 읽은 결과다(CursorResponse).
 */
public record CommentPage(List<CommentView> fetched, long totalCount) {
}
