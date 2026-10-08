package com.nhnacademy.blog.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 테스트 도우미. 개발용 메일은 로그로만 나가므로 코드는 DB에서 읽는다.
 */
@Component
public class TestEmails {

    private final JdbcTemplate jdbcTemplate;

    public TestEmails(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 테스트끼리 DB와 Redis를 함께 쓰므로 매번 다른 이메일을 쓴다. */
    public static String unique() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    public String latestCode(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM email_verification WHERE email = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                String.class, email);
    }

    public void expireLatest(String email) {
        jdbcTemplate.update("UPDATE email_verification SET expires_at = NOW() - INTERVAL 1 MINUTE WHERE email = ?",
                email);
    }

}
