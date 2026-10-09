package com.nhnacademy.blog.tag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * post_tag의 복합 키 (post_id, tag_id). JPA는 복합 키 클래스에 Serializable과 equals·hashCode를 요구한다.
 * 값은 PostTag의 @MapsId가 연관된 글·태그에서 채운다.
 */
@Embeddable
public class PostTagId implements Serializable {

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "tag_id")
    private Long tagId;

    protected PostTagId() {
    }

    public Long getPostId() {
        return postId;
    }

    public Long getTagId() {
        return tagId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PostTagId id
                && Objects.equals(postId, id.postId) && Objects.equals(tagId, id.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, tagId);
    }

}
