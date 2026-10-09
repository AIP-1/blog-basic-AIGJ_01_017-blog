package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.post.domain.Topic;

/** 주제 하나 `{ code, name }` (GET /api/topics). code는 글 저장 본문의 topic 값이다. */
public record TopicResponse(String code, String name) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(topic.name(), topic.getDisplayName());
    }

}
