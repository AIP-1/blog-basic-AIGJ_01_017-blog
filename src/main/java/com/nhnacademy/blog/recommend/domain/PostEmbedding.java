package com.nhnacademy.blog.recommend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 글 임베딩 post_embedding (T126). 가까운 글을 글의 가시성 조건과 한 쿼리에서 찾으려고(PostEmbeddingRepository.findNear)
 * 엔티티로 읽는다. 쓰기(넣기·바꾸기·지우기)는 PostEmbeddingRepository가 SQL로 하므로 읽기 전용이다.
 * embedding은 hibernate-vector가 pgvector의 vector 칸을 float[]로 읽고 쓴다.
 */
@Entity
@Immutable
@Table(name = "post_embedding")
public class PostEmbedding {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    @Column(name = "embedding", nullable = false)
    private float[] embedding;

    protected PostEmbedding() {
    }

    public Long getPostId() {
        return postId;
    }

    public float[] getEmbedding() {
        return embedding;
    }

}
