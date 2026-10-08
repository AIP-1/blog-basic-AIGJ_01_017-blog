package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.post.domain.Post;

/** 발행·수정 응답 { id, status, url }. url은 글 주소({주소}.blog.com/{id})다. */
public record PostSavedResponse(Long id, String status, String url) {

    public static PostSavedResponse of(Post post, String url) {
        return new PostSavedResponse(post.getId(), post.getStatus().name(), url);
    }

}
