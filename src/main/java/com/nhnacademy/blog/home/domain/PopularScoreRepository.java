package com.nhnacademy.blog.home.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 인기 점수 집계 (HOME-02, data-model 인기 점수). 엔티티 하나가 아니라 세 테이블(view_log, post_like, comment)의
 * 최근 행을 모아 세는 쿼리라 JPA 대신 SQL로 쓴다.
 * <p>
 * 최근 활동 행에서 출발한다. 세 테이블 모두 시각 칼럼에 인덱스가 있어(idx_view_log_viewed_at 등)
 * 최근 1시간 행만 읽고, 글 전체를 훑지 않는다. 각 행을 가중치 하나로 바꿔 UNION ALL로 이어 붙인 뒤
 * 글별로 더하면 "조회 수×1 + 공감 수×3 + 댓글 수×5"가 된다.
 */
@Repository
public class PopularScoreRepository {

    /**
     * 글 자체 조건(발행된 공개 글, 삭제·숨김 아님)은 여기서 미리 걸러 후보 자리를 아낀다.
     * 블로그·주인 상태까지 포함한 최종 가시성 판단은 부르는 쪽이 PostSpecifications로 다시 한다.
     */
    private static final String SQL = """
            SELECT activity.post_id, SUM(activity.weight) AS score
            FROM (
                SELECT post_id, :viewWeight AS weight FROM view_log WHERE viewed_at >= :since
                UNION ALL
                SELECT post_id, :likeWeight FROM post_like WHERE created_at >= :since
                UNION ALL
                SELECT post_id, :commentWeight FROM comment
                WHERE created_at >= :since AND deleted_at IS NULL AND is_blinded = 0
            ) activity
            JOIN post p ON p.id = activity.post_id
            WHERE p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL AND p.is_blinded = 0
            GROUP BY activity.post_id
            ORDER BY score DESC, activity.post_id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PopularScoreRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** since 이후 활동으로 점수가 높은 글 limit개. 점수가 같으면 나중에 쓴 글(id가 큰 글)이 위다. 활동이 없는 글은 없다. */
    public List<PostScore> topScores(LocalDateTime since, int viewWeight, int likeWeight, int commentWeight,
                                     int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", since)
                .addValue("viewWeight", viewWeight)
                .addValue("likeWeight", likeWeight)
                .addValue("commentWeight", commentWeight)
                .addValue("limit", limit);
        return jdbcTemplate.query(SQL, params,
                (row, rowNum) -> new PostScore(row.getLong("post_id"), row.getLong("score")));
    }

}
