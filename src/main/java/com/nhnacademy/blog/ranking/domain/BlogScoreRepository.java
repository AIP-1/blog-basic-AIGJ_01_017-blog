package com.nhnacademy.blog.ranking.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 블로그 점수 (T079, HOME-04, R-13): [since, until) 동안 그 블로그의 공개 글이 받은 조회×1 + 공감×3 + 댓글×5 + 새 구독자×10.
 * 글 조건(발행된 공개 글, 삭제·숨김 아님)과 블로그 조건(삭제·이용 제한·이사 아님, 주인이 정지·탈퇴 아님, 공개 글이 하나라도 있음)을
 * 여기서 먼저 걸러 100자리를 아낀다. 읽을 때 가시성을 한 번 더 본다(RankingService).
 * 끝(until)을 정해 두므로 같은 시간 칸 안에서는 몇 번 계산해도 같은 순위가 나온다(BloggerRanking).
 */
@Repository
public class BlogScoreRepository {

    private static final String SQL = """
            SELECT s.blog_id, SUM(s.weight) AS score
            FROM (
                SELECT p.blog_id, a.weight
                FROM (
                    SELECT post_id, :viewWeight AS weight FROM view_log WHERE viewed_at >= :since AND viewed_at < :until
                    UNION ALL
                    SELECT post_id, :likeWeight FROM post_like WHERE created_at >= :since AND created_at < :until
                    UNION ALL
                    SELECT post_id, :commentWeight FROM comment
                    WHERE created_at >= :since AND created_at < :until AND deleted_at IS NULL AND is_blinded = FALSE
                ) a
                JOIN post p ON p.id = a.post_id
                WHERE p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL AND p.is_blinded = FALSE
                UNION ALL
                SELECT blog_id, :subscribeWeight FROM subscription WHERE created_at >= :since AND created_at < :until
            ) s
            JOIN blog b ON b.id = s.blog_id
            JOIN member m ON m.id = b.member_id
            WHERE b.deleted_at IS NULL AND b.is_restricted = FALSE AND b.moved_to_blog_id IS NULL
              AND m.status <> 'WITHDRAWN'
              AND NOT (m.status = 'SUSPENDED' AND (m.suspended_until IS NULL OR m.suspended_until > :until))
              AND EXISTS (SELECT 1 FROM post p2 WHERE p2.blog_id = b.id AND p2.status = 'PUBLISHED'
                          AND p2.visibility = 'PUBLIC' AND p2.deleted_at IS NULL AND p2.is_blinded = FALSE)
            GROUP BY s.blog_id
            ORDER BY score DESC, s.blog_id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BlogScoreRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BlogScore> topScores(LocalDateTime since, LocalDateTime until, Weights weights, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", since)
                .addValue("until", until)
                .addValue("viewWeight", weights.view())
                .addValue("likeWeight", weights.like())
                .addValue("commentWeight", weights.comment())
                .addValue("subscribeWeight", weights.subscribe())
                .addValue("limit", limit);
        return jdbcTemplate.query(SQL, params, (row, i) -> new BlogScore(row.getLong("blog_id"), row.getLong("score")));
    }

    public record Weights(int view, int like, int comment, int subscribe) {
    }

}
