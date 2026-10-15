package com.nhnacademy.blog.manage.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 방문 통계 (T082·T083, MNG-03, R-14). 엔티티 없이 SQL로 다룬다(행을 하나씩 다루는 일이 없고, 모으고 세는 일만 있어서).
 * blog_visit: 블로그·날짜·방문자마다 한 행(UNIQUE)이라 ON CONFLICT DO NOTHING으로 하루 한 번만 센다.
 * blog_daily_stat·blog_referrer_daily: 새벽 작업이 전날까지를 모아 둔 값. blog.total_visitor_count: 모아 둔 날들의 합.
 */
@Repository
public class BlogStatsRepository {

    private final JdbcTemplate jdbc;

    public BlogStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 방문 하나. 같은 날 같은 방문자는 두 번째부터 무시한다(넣은 행 수 0). */
    public int recordVisit(Long blogId, LocalDate date, String visitorKey, ReferrerType type, String host) {
        return jdbc.update("""
                INSERT INTO blog_visit (blog_id, visit_date, visitor_key, referrer_type, referrer_host)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING""", blogId, date, visitorKey, type.name(), host);
    }

    // ---------- 새벽 모으기 ----------

    /** 새벽 모으기 잠금 번호. advisory lock은 번호에 거는 잠금이고 번호의 뜻은 앱이 정한다(이 앱에서 쓰는 번호는 이것 하나). */
    private static final long AGGREGATION_LOCK = 1L;

    /**
     * 새벽 모으기 잠금을 잡는다(PostgreSQL pg_try_advisory_xact_lock). 다른 앱이 이미 잡고 있으면 기다리지 않고 false다.
     * 잡은 잠금은 이 트랜잭션이 끝날 때 저절로 풀리므로 트랜잭션 안에서 불러야 한다(밖에서 부르면 바로 풀려 뜻이 없다).
     */
    public boolean tryLockAggregation() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class,
                AGGREGATION_LOCK));
    }

    /** 그날의 방문자 수·글 조회 수를 블로그마다 모은다. 다시 돌려도 같은 결과가 되게 먼저 지우고 넣는다. */
    public void aggregateDay(LocalDate date) {
        jdbc.update("DELETE FROM blog_daily_stat WHERE stat_date = ?", date);
        jdbc.update("""
                INSERT INTO blog_daily_stat (blog_id, stat_date, visitor_count, view_count)
                SELECT blog_id, ?, SUM(visitors), SUM(views) FROM (
                    SELECT blog_id, COUNT(*) AS visitors, 0 AS views FROM blog_visit WHERE visit_date = ? GROUP BY blog_id
                    UNION ALL
                    SELECT p.blog_id, 0, COUNT(*) FROM view_log l JOIN post p ON p.id = l.post_id
                    WHERE l.viewed_at >= ? AND l.viewed_at < ? GROUP BY p.blog_id
                ) t GROUP BY blog_id""", date, date, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        jdbc.update("DELETE FROM blog_referrer_daily WHERE stat_date = ?", date);
        jdbc.update("""
                INSERT INTO blog_referrer_daily (blog_id, stat_date, referrer_type, referrer_host, visit_count)
                SELECT blog_id, ?, referrer_type, referrer_host, COUNT(*) FROM blog_visit
                WHERE visit_date = ? GROUP BY blog_id, referrer_type, referrer_host""", date, date);
    }

    /** 누적 방문자 = 모아 둔 날들의 합(오늘은 빼고). 다시 계산하므로 두 번 돌려도 두 번 더해지지 않는다. */
    public void refreshTotals() {
        jdbc.update("""
                UPDATE blog b SET total_visitor_count = s.total
                FROM (SELECT blog_id, SUM(visitor_count) AS total FROM blog_daily_stat GROUP BY blog_id) s
                WHERE s.blog_id = b.id""");
    }

    /** 오래된 방문 기록 지우기(모은 뒤 7일 보관). */
    public int deleteVisitsBefore(LocalDate date) {
        return jdbc.update("DELETE FROM blog_visit WHERE visit_date < ?", date);
    }

    /** 오래된 조회 기록 지우기(인기 점수 1시간, 블로그 점수 7일만 쓴다, T079). */
    public int deleteViewsBefore(LocalDateTime time) {
        return jdbc.update("DELETE FROM view_log WHERE viewed_at < ?", time);
    }

    // ---------- 읽기 ----------

    public record Day(LocalDate date, long visitors, long views) {
    }

    /** 모아 둔 일별 값(그날 행이 없으면 빠진다). */
    public List<Day> dailyStats(Long blogId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT stat_date, visitor_count, view_count FROM blog_daily_stat
                WHERE blog_id = ? AND stat_date BETWEEN ? AND ? ORDER BY stat_date""",
                (rs, i) -> new Day(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3)), blogId, from, to);
    }

    /** 오늘처럼 아직 모으지 않은 날의 값을 바로 센다. */
    public Day liveDay(Long blogId, LocalDate date) {
        Long visitors = jdbc.queryForObject("SELECT COUNT(*) FROM blog_visit WHERE blog_id = ? AND visit_date = ?",
                Long.class, blogId, date);
        Long views = jdbc.queryForObject("""
                SELECT COUNT(*) FROM view_log l JOIN post p ON p.id = l.post_id
                WHERE p.blog_id = ? AND l.viewed_at >= ? AND l.viewed_at < ?""",
                Long.class, blogId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        return new Day(date, visitors == null ? 0 : visitors, views == null ? 0 : views);
    }

    public record Referrer(ReferrerType type, String host, long count) {
    }

    /** 모아 둔 유입 경로(종류·호스트별 합). */
    public List<Referrer> referrers(Long blogId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT referrer_type, referrer_host, SUM(visit_count) FROM blog_referrer_daily
                WHERE blog_id = ? AND stat_date BETWEEN ? AND ? GROUP BY referrer_type, referrer_host""",
                (rs, i) -> new Referrer(ReferrerType.valueOf(rs.getString(1)), rs.getString(2), rs.getLong(3)),
                blogId, from, to);
    }

    /** 아직 모으지 않은 날(오늘)의 유입 경로. */
    public List<Referrer> liveReferrers(Long blogId, LocalDate date) {
        return jdbc.query("""
                SELECT referrer_type, referrer_host, COUNT(*) FROM blog_visit
                WHERE blog_id = ? AND visit_date = ? GROUP BY referrer_type, referrer_host""",
                (rs, i) -> new Referrer(ReferrerType.valueOf(rs.getString(1)), rs.getString(2), rs.getLong(3)),
                blogId, date);
    }

    public record PostViews(Long postId, String title, long views) {
    }

    /** 최근 since 이후 조회 수가 많은 글 limit개(지우지 않은 발행 글). */
    public List<PostViews> recentPopular(Long blogId, LocalDateTime since, int limit) {
        return jdbc.query("""
                SELECT p.id, p.title, COUNT(*) AS views FROM view_log l JOIN post p ON p.id = l.post_id
                WHERE p.blog_id = ? AND l.viewed_at >= ? AND p.deleted_at IS NULL AND p.status = 'PUBLISHED'
                GROUP BY p.id, p.title ORDER BY views DESC, p.id DESC LIMIT ?""",
                (rs, i) -> new PostViews(rs.getLong(1), rs.getString(2), rs.getLong(3)), blogId, since, limit);
    }

}
