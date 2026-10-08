package com.nhnacademy.blog.reaction.application;

/** 공감을 켜거나 끈 뒤의 상태. likeCount는 DB에서 다시 읽은 실제 값이다. */
public record LikeResult(boolean liked, int likeCount) {
}
