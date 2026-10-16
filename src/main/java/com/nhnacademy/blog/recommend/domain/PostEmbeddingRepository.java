package com.nhnacademy.blog.recommend.domain;

import com.nhnacademy.blog.post.domain.Post;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 글 임베딩 post_embedding (T069b, T069c, T121, T126). 주 DB(PostgreSQL + pgvector)의 표다.
 * <ul>
 *   <li>쓰기는 SQL로 한다. 벡터는 pgvector의 글자 표기 '[0.1,0.2,...]'로 보내고 SQL에서 vector로 바꾼다(CAST).</li>
 *   <li>가까운 글 찾기는 Criteria로 글(Post)과 임베딩(PostEmbedding)을 한 쿼리에 묶는다. 그래야 가시성 조건(PostSpecifications)을
 *       글 목록과 같은 코드로 같은 쿼리 안에서 걸 수 있다. 거리는 pgvector의 cosine_distance(= {@code <=>})다.</li>
 * </ul>
 */
@Repository
public class PostEmbeddingRepository {

    /** SQL 이름 붙인 매개변수 :postId */
    private static final String POST_ID = "postId";

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;

    public PostEmbeddingRepository(NamedParameterJdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    /** 없으면 넣고, 있으면 바꾼다(글을 고칠 때). textVersion은 넣은 글자의 방식 번호다(PostEmbeddingService.TEXT_VERSION). */
    public void save(long postId, float[] embedding, int textVersion) {
        jdbc.update("""
                INSERT INTO post_embedding (post_id, embedding, text_version, updated_at)
                VALUES (:postId, CAST(:embedding AS vector), :textVersion, now())
                ON CONFLICT (post_id) DO UPDATE
                    SET embedding = EXCLUDED.embedding, text_version = EXCLUDED.text_version, updated_at = now()
                """, Map.of(POST_ID, postId, "embedding", toLiteral(embedding), "textVersion", textVersion));
    }

    public void delete(long postId) {
        jdbc.update("DELETE FROM post_embedding WHERE post_id = :postId", Map.of(POST_ID, postId));
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

    /** 이 글의 임베딩. 아직 만들지 않았으면 빈 값. */
    public Optional<float[]> findEmbedding(long postId) {
        return Optional.ofNullable(entityManager.find(PostEmbedding.class, postId)).map(PostEmbedding::getEmbedding);
    }

    /**
     * query와 임베딩이 가까운 글 가운데 visible에 맞는 글을 가까운 순(같으면 나중 글 먼저)으로 offset부터 limit개.
     *
     * @param minSimilarity 코사인 유사도가 이보다 낮은 글은 뺀다. null이면 빼지 않는다
     * @param excludePostId 이 글은 뺀다(비슷한 글에서 자기 자신). null이면 빼지 않는다
     * @param fetch         글과 함께 읽을 연관(목록 한 줄에 나가는 blog, category)
     */
    public List<NearPost> findNear(float[] query, Specification<Post> visible, Double minSimilarity, Long excludePostId,
                                   int offset, int limit, String... fetch) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> criteria = cb.createTupleQuery();
        Root<Post> post = criteria.from(Post.class);
        for (String path : fetch) {
            // 미분류 글(category 없음)도 나오게 LEFT. 그냥 fetch하면 INNER JOIN이라 빠진다
            post.fetch(path, JoinType.LEFT);
        }
        Root<PostEmbedding> embedding = criteria.from(PostEmbedding.class);
        Expression<Double> distance = distance(cb, embedding, query);
        criteria.multiselect(post, distance)
                .where(conditions(cb, criteria, post, embedding, distance, visible, minSimilarity, excludePostId))
                .orderBy(cb.asc(distance), cb.desc(post.get("id")));
        List<NearPost> result = new ArrayList<>();
        for (Tuple row : entityManager.createQuery(criteria).setFirstResult(offset).setMaxResults(limit).getResultList()) {
            result.add(new NearPost(row.get(0, Post.class), 1 - row.get(1, Double.class)));
        }
        return result;
    }

    /** findNear와 같은 조건에 맞는 글 수. */
    public long countNear(float[] query, Specification<Post> visible, Double minSimilarity, Long excludePostId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> criteria = cb.createQuery(Long.class);
        Root<Post> post = criteria.from(Post.class);
        Root<PostEmbedding> embedding = criteria.from(PostEmbedding.class);
        Expression<Double> distance = distance(cb, embedding, query);
        criteria.select(cb.count(post))
                .where(conditions(cb, criteria, post, embedding, distance, visible, minSimilarity, excludePostId));
        return entityManager.createQuery(criteria).getSingleResult();
    }

    /**
     * 코사인 거리(0 = 같은 방향, 1 - 코사인 유사도). 작을수록 비슷하다.
     * 검색어 벡터는 임베딩 칸에서 타입(vector)을 물려받는 값으로 넘긴다. cb.literal로 그냥 넘기면 float[] 배열 타입이 되어
     * hibernate-vector가 "cosine_distance의 인자는 vector여야 한다"며 받지 않는다. 표준 JPA와 HibernateCriteriaBuilder에는
     * 타입을 물려받는 값 만들기가 없어 Hibernate 구현(SqmCriteriaNodeBuilder.value)을 쓴다.
     */
    private static Expression<Double> distance(CriteriaBuilder cb, Root<PostEmbedding> embedding, float[] query) {
        SqmExpression<float[]> column = (SqmExpression<float[]>) embedding.<float[]>get("embedding");
        return cb.function("cosine_distance", Double.class, column, ((SqmCriteriaNodeBuilder) cb).value(query, column));
    }

    private static Predicate[] conditions(CriteriaBuilder cb, CriteriaQuery<?> criteria, Root<Post> post,
                                          Root<PostEmbedding> embedding, Expression<Double> distance,
                                          Specification<Post> visible, Double minSimilarity, Long excludePostId) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(embedding.get("postId"), post.get("id")));
        conditions.add(visible.toPredicate(post, criteria, cb));
        if (minSimilarity != null) {
            conditions.add(cb.le(distance, 1 - minSimilarity));
        }
        if (excludePostId != null) {
            conditions.add(cb.notEqual(post.get("id"), excludePostId));
        }
        return conditions.toArray(Predicate[]::new);
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
