package com.nhnacademy.blog.comment.presentation.dto;

import com.nhnacademy.blog.comment.application.CommentView;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comment (contracts/rest-api.md 주요 응답 객체). state가 NORMAL이 아니면 content·author는 null이다.
 * 수정(CMT-03)은 아직 없어 canEdit은 false, 답글(CMT-05)은 스텝 7이라 replies는 비어 있다.
 */
public record CommentResponse(Long id, Long parentId, MemberSummaryResponse author, String content, boolean secret,
                              String state, OffsetDateTime createdAt, OffsetDateTime updatedAt, Viewer viewer,
                              Map<String, String> blind, List<CommentResponse> replies) {

    public record Viewer(boolean canEdit, boolean canDelete) {
    }

    public static CommentResponse from(CommentView view) {
        Comment comment = view.comment();
        boolean shows = view.showsContent();
        return new CommentResponse(comment.getId(), comment.getParent() == null ? null : comment.getParent().getId(),
                shows ? MemberSummaryResponse.of(comment.getMember(), view.authorPrimaryBlogAddress()) : null,
                shows ? comment.getContent() : null, comment.isSecret(), view.state().name(),
                DateTimes.toOffset(comment.getCreatedAt()),
                comment.getUpdatedAt().equals(comment.getCreatedAt()) ? null : DateTimes.toOffset(comment.getUpdatedAt()),
                new Viewer(false, view.canDelete()), view.blind(), List.of());
    }

}
