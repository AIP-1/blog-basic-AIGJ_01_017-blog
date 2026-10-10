package com.nhnacademy.blog.comment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 쓰기 (CMT-01). 내용은 1~1,000자.
 * parentId가 있으면 그 댓글의 답글이다(CMT-05, 한 단계). secret이 true면 비밀댓글이다(CMT-06, 스텝 17):
 * 글 주인과 작성자만 내용을 본다. 보내지 않으면 공개 댓글이다.
 */
public record CommentRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = 1000, message = "댓글은 1,000자까지입니다.")
        String content,

        Long parentId,

        Boolean secret) {

    public boolean isSecret() {
        return Boolean.TRUE.equals(secret);
    }

}
