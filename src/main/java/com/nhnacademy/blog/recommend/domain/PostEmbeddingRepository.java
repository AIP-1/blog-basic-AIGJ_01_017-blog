package com.nhnacademy.blog.recommend.domain;

import com.nhnacademy.blog.recommend.config.RecommendDataSourceConfig;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL post_embedding (T069b, T069c). JPA는 주 DB(MySQL)만 쓰므로 여기는 JDBC로 SQL을 그대로 쓴다.
 * 벡터는 pgvector의 글자 표기 '[0.1,0.2,...]'로 보내고 SQL에서 vector로 바꾼다(CAST).
 */
@ConditionalOnProperty(name = "app.recommend.enabled", havingValue = "true", matchIfMissing = true)
@Repository
public class PostEmbeddingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PostEmbeddingRepository(@Qualifier(RecommendDataSourceConfig.QUALIFIER) NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 없으면 넣고, 있으면 바꾼다(글을 고칠 때). textVersion은 넣은 글자의 방식 번호다(PostEmbeddingService.TEXT_VERSION). */
    public void save(long postId, float[] embedding, int textVersion) {
        jdbc.update("""
                INSERT INTO post_embedding (post_id, embedding, text_version, updated_at)
                VALUES (:postId, CAST(:embedding AS vector), :textVersion, now())
                ON CONFLICT (post_id) DO UPDATE
                    SET embedding = EXCLUDED.embedding, text_version = EXCLUDED.text_version, updated_at = now()
                """, Map.of("postId", postId, "embedding", toLiteral(embedding), "textVersion", textVersion));
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

    /** 이 방식으로 넣은 글자로 만든 임베딩의 글 번호. 다른 번호는 옛 방식이라 다시 만든다. */
    public List<Long> findPostIdsByTextVersion(int textVersion) {
        return jdbc.queryForList("SELECT post_id FROM post_embedding WHERE text_version = :textVersion",
                Map.of("textVersion", textVersion), Long.class);
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

    /**
     * 검색어 임베딩과 가까운 글을 가까운 순으로 limit개 (의미 검색, T121). 코사인 유사도가 minSimilarity보다 낮은 글은 뺀다.
     * within이 있으면 그 글 번호 안에서만 찾는다(블로그 안 검색: 이 표는 블로그를 모르므로 MySQL이 고른 번호를 받는다).
     * HNSW 인덱스는 한 번에 hnsw.ef_search개까지만 돌려주므로, 연결마다 그 값을 limit보다 크게 둔다(RecommendDataSourceConfig).
     */
    public List<NearPost> findNearestTo(float[] query, double minSimilarity, int limit, Collection<Long> within) {
        if (within != null && within.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", toLiteral(query))
                .addValue("maxDistance", 1 - minSimilarity)
                .addValue("limit", limit);
        String onlyWithin = "";
        if (within != null) {
            onlyWithin = "AND post_id IN (:within)";
            params.addValue("within", within);
        }
        return jdbc.query("""
                SELECT post_id, 1 - (embedding <=> CAST(:query AS vector)) AS similarity
                FROM post_embedding
                WHERE embedding <=> CAST(:query AS vector) <= :maxDistance
                """ + onlyWithin + """
                ORDER BY embedding <=> CAST(:query AS vector), post_id DESC
                LIMIT :limit
                """, params, (row, rowNum) -> new NearPost(row.getLong("post_id"), row.getDouble("similarity")));
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
