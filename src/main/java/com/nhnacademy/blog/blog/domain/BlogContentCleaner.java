package com.nhnacademy.blog.blog.domain;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 블로그를 지울 때 그 블로그의 남은 글을 함께 지운다 (BLOG-07, AUTH-06). 글 하나 삭제(POST-03)와 같은 규칙을 블로그 단위
 * 문장 몇 개로: 글의 공감·알림은 지우고, 글과 그 글의 댓글은 소프트 삭제한다. 작성자가 고친 것이 아니라 수정 시각은 그대로 둔다.
 * 글을 하나씩 엔티티로 읽어 지우면 글이 많은 블로그에서 문장이 글 수만큼 나가서, 블로그 단위 SQL로 한다.
 */
@Repository
public class BlogContentCleaner {

    private final JdbcTemplate jdbcTemplate;

    public BlogContentCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 지운 글 수. 같은 트랜잭션 안에서 부른다(JPA와 같은 연결이다). */
    public int deletePosts(Long blogId, LocalDateTime now) {
        jdbcTemplate.update("""
                DELETE FROM post_like l USING post p
                WHERE p.id = l.post_id AND p.blog_id = ? AND p.deleted_at IS NULL""", blogId);
        jdbcTemplate.update("""
                DELETE FROM notification n USING post p
                WHERE n.target_type = 'POST' AND n.target_id = p.id AND p.blog_id = ? AND p.deleted_at IS NULL""", blogId);
        jdbcTemplate.update("""
                DELETE FROM notification n USING comment c, post p
                WHERE n.target_type = 'COMMENT' AND n.target_id = c.id AND p.id = c.post_id
                  AND p.blog_id = ? AND p.deleted_at IS NULL""", blogId);
        jdbcTemplate.update("""
                UPDATE comment c SET deleted_at = ?
                FROM post p
                WHERE p.id = c.post_id AND p.blog_id = ? AND p.deleted_at IS NULL AND c.deleted_at IS NULL""", now, blogId);
        return jdbcTemplate.update("""
                UPDATE post SET deleted_at = ?
                WHERE blog_id = ? AND deleted_at IS NULL""", now, blogId);
    }

}
