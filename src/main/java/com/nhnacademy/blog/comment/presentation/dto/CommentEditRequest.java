package com.nhnacademy.blog.comment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 댓글·방명록 고치기 (CMT-03, CMT-04). 내용만 바꾼다. 1~1,000자. */
public record CommentEditRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 1000, message = "1,000자까지 쓸 수 있습니다.")
        String content) {
}
