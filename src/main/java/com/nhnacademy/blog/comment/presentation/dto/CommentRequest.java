package com.nhnacademy.blog.comment.presentation.dto;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 쓰기 (CMT-01). 내용은 1~1,000자.
 * parentId가 있으면 그 댓글의 답글이다(CMT-05, 한 단계). 비밀댓글(secret, CMT-06)은 백로그라 아직 보내면 400이다.
 */
public record CommentRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = 1000, message = "댓글은 1,000자까지입니다.")
        String content,

        Long parentId,

        @AssertFalse(message = "비밀댓글은 아직 쓸 수 없습니다.")
        Boolean secret) {
}
