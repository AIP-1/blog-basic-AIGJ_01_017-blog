package com.nhnacademy.blog.comment.presentation.dto;

import com.nhnacademy.blog.comment.application.CommentView;
import com.nhnacademy.blog.comment.domain.CommentEntry;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comment (contracts/rest-api.md 주요 응답 객체). 댓글과 방명록이 같은 모양이다.
 * state가 NORMAL이 아니면 content·author는 null이다. updatedAt은 고친 적이 없으면 null(CMT-03).
 * replies는 답글(CMT-05)이고, 답글 자신의 replies는 비어 있다.
 */
public record CommentResponse(Long id, Long parentId, MemberSummaryResponse author, String content, boolean secret,
                              String state, OffsetDateTime createdAt, OffsetDateTime updatedAt, Viewer viewer,
                              Map<String, String> blind, List<CommentResponse> replies) {

    public record Viewer(boolean canEdit, boolean canDelete) {
    }

    public static CommentResponse from(CommentView view) {
        CommentEntry entry = view.entry();
        boolean shows = view.showsContent();
        return new CommentResponse(entry.getId(), entry.getParentId(),
                shows ? MemberSummaryResponse.of(entry.getMember(), view.authorPrimaryBlogAddress(),
                        view.authorProfileImageUrl()) : null,
                shows ? entry.getContent() : null, entry.isSecret(), view.state().name(),
                DateTimes.toOffset(entry.getCreatedAt()),
                entry.getUpdatedAt().equals(entry.getCreatedAt()) ? null : DateTimes.toOffset(entry.getUpdatedAt()),
                new Viewer(view.canEdit(), view.canDelete()), view.blind(),
                view.replies().stream().map(CommentResponse::from).toList());
    }

}
