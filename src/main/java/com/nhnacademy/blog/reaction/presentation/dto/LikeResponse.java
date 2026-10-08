package com.nhnacademy.blog.reaction.presentation.dto;

import com.nhnacademy.blog.reaction.application.LikeResult;

/** { liked, likeCount }. 화면은 이 값으로 버튼과 숫자를 바꾼다. */
public record LikeResponse(boolean liked, int likeCount) {

    public static LikeResponse from(LikeResult result) {
        return new LikeResponse(result.liked(), result.likeCount());
    }

}
