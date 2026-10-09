package com.nhnacademy.blog.recommend.domain;

import com.nhnacademy.blog.recommend.config.RecommendDataSourceConfig;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL post_embedding (T069b, T069c). JPA는 주 DB(MySQL)만 쓰므로 여기는 JDBC로 SQL을 그대로 쓴다.
 * 벡터는 pgvector의 글자 표기 '[0.1,0.2,...]'로 보내고 SQL에서 vector로 바꾼다(CAST).
 */
@Repository
public class PostEmbeddingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PostEmbeddingRepository(@Qualifier(RecommendDataSourceConfig.QUALIFIER) NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 없으면 넣고, 있으면 바꾼다(글을 고칠 때). */
    public void save(long postId, float[] embedding) {
        jdbc.update("""
                INSERT INTO post_embedding (post_id, embedding, updated_at)
                VALUES (:postId, CAST(:embedding AS vector), now())
                ON CONFLICT (post_id) DO UPDATE SET embedding = EXCLUDED.embedding, updated_at = now()
                """, Map.of("postId", postId, "embedding", toLiteral(embedding)));
    }

    public void delete(long postId) {
        jdbc.update("DELETE FROM post_embedding WHERE post_id = :postId", Map.of("postId", postId));
    }

    public void deleteAll(Collection<Long> postIds) {
        if (!postIds.isEmpty()) {
            jdbc.update("DELETE FROM post_embedding WHERE post_id IN (:postIds)", Map.of("postIds", postIds));
        }
    }

    public List<Long> findAllPostIds() {
        return jdbc.queryForList("SELECT post_id FROM post_embedding", Map.of(), Long.class);
    }

    /**
     * 이 글과 임베딩이 가까운 다른 글 번호를 가까운 순으로 limit개. 이 글의 임베딩이 없으면 빈 목록.
     * {@code <=>}는 pgvector의 코사인 거리(1 - 코사인 유사도)다. 작을수록 비슷하다.
     */
    public List<Long> findNearest(long postId, int limit) {
        return jdbc.queryForList("""
                SELECT other.post_id
                FROM post_embedding me
                JOIN post_embedding other ON other.post_id <> me.post_id
                WHERE me.post_id = :postId
                ORDER BY other.embedding <=> me.embedding, other.post_id DESC
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("postId", postId).addValue("limit", limit), Long.class);
    }

    /** float[] → '[0.1,0.2,...]' (pgvector가 받는 글자 모양). */
    static String toLiteral(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

}
