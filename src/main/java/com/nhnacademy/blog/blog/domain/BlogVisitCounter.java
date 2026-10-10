package com.nhnacademy.blog.blog.domain;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사이드바 방문자 수 모듈의 숫자 (BLOG-05: 오늘·어제·누적). blog_visit(오늘 방문, 블로그·날짜·방문자마다 한 행),
 * blog_daily_stat(어제까지 모은 수), blog.total_visitor_count(어제까지의 누적)를 읽는다.
 * 방문을 남기는 쪽과 새벽 집계는 스텝 20(T082)이라, 그 전에는 모두 0이다. 엔티티가 아직 없는 표라 SQL로 센다.
 */
@Repository
public class BlogVisitCounter {

    private final JdbcTemplate jdbcTemplate;

    public BlogVisitCounter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long visitors(Long blogId, LocalDate date) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blog_visit WHERE blog_id = ? AND visit_date = ?", Long.class, blogId, date);
        return count == null ? 0 : count;
    }

    /** 모은 값이 있으면 그것, 아직 모으기 전이면 blog_visit을 센다(7일 보관이라 어제 행은 남아 있다). */
    public long visitorsOf(Long blogId, LocalDate date) {
        Long stat = jdbcTemplate.query(
                "SELECT visitor_count FROM blog_daily_stat WHERE blog_id = ? AND stat_date = ?",
                rs -> rs.next() ? rs.getLong(1) : null, blogId, date);
        return stat != null ? stat : visitors(blogId, date);
    }

}
