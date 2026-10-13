package com.nhnacademy.blog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class BlogApplicationTests extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesAllErdTables() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                Integer.class);

        // ERD의 26개와 글 임베딩(post_embedding)
        assertThat(tables).isEqualTo(27);
    }

}
