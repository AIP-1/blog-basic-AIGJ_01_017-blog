package com.nhnacademy.blog.comment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 방명록 쓰기 (CMT-04). 댓글과 같이 1~1,000자, parentId가 있으면 그 글의 답글(한 단계).
 * secret이 true면 블로그 주인과 작성자만 내용을 본다. 보내지 않으면 공개 글이다.
 */
public record GuestbookRequest(
        @NotBlank(message = "방명록 내용을 입력해 주세요.")
        @Size(max = 1000, message = "방명록은 1,000자까지입니다.")
        String content,

        Long parentId,

        Boolean secret) {

    public boolean isSecret() {
        return Boolean.TRUE.equals(secret);
    }

}
