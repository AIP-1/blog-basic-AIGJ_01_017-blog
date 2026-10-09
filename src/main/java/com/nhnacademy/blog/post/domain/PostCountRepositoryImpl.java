package com.nhnacademy.blog.post.domain;

import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.tag.domain.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/**
 * select category_id, count(*) from post where (가시성 조건) group by category_id
 * select pt.tag_id, count(*) from post join post_tag pt where (가시성 조건) group by pt.tag_id
 */
class PostCountRepositoryImpl implements PostCountRepository {

    private final EntityManager entityManager;

    PostCountRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Map<Long, Long> countByCategory(Specification<Post> condition) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Post> post = query.from(Post.class);
        Join<Post, Category> category = post.join("category", JoinType.LEFT);
        query.multiselect(category.get("id"), cb.count(post))
                .where(condition.toPredicate(post, query, cb))
                .groupBy(category.get("id"));

        Map<Long, Long> counts = new HashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            counts.put(row.get(0, Long.class), row.get(1, Long.class));
        }
        return counts;
    }

    @Override
    public Map<Long, Long> countByTag(Specification<Post> condition) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Post> post = query.from(Post.class);
        Join<Post, Tag> tag = post.join("tags");
        query.multiselect(tag.get("id"), cb.count(post))
                .where(condition.toPredicate(post, query, cb))
                .groupBy(tag.get("id"));

        Map<Long, Long> counts = new HashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            counts.put(row.get(0, Long.class), row.get(1, Long.class));
        }
        return counts;
    }

}
