package com.nhnacademy.blog.manage.presentation.dto;

import com.nhnacademy.blog.comment.presentation.dto.CommentResponse;
import com.nhnacademy.blog.manage.application.ReceivedComment;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 받은 댓글·방명록 한 줄 (contracts/rest-api.md `[{ ...Comment, post: { id, title } | null }]`, MNG-02).
 * Comment와 같은 칸에 post를 더한다. 방명록이면 post가 null이다. 낱개 목록이라 replies는 늘 비어 있다.
 */
public record ReceivedCommentResponse(Long id, Long parentId, MemberSummaryResponse author, String content,
                                      boolean secret, String state, OffsetDateTime createdAt,
                                      OffsetDateTime updatedAt, CommentResponse.Viewer viewer,
                                      Map<String, String> blind, List<CommentResponse> replies, PostRef post) {

    public record PostRef(Long id, String title) {
    }

    public static ReceivedCommentResponse from(ReceivedComment row) {
        CommentResponse comment = CommentResponse.from(row.view());
        return new ReceivedCommentResponse(comment.id(), comment.parentId(), comment.author(), comment.content(),
                comment.secret(), comment.state(), comment.createdAt(), comment.updatedAt(), comment.viewer(),
                comment.blind(), comment.replies(),
                row.postId() == null ? null : new PostRef(row.postId(), row.postTitle()));
    }

}
