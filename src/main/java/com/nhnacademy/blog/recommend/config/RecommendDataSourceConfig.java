package com.nhnacademy.blog.recommend.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 추천 전용 두 번째 DB 연결 (T069a, R-02). 주 데이터는 MySQL 하나이고, PostgreSQL에는 글 id와 임베딩만 둔다.
 * <p>
 * DataSource를 하나 더 만들면 보통 Spring Boot가 주 DB 자동 설정을 멈춘다("DataSource가 이미 있네").
 * {@code @Bean(defaultCandidate = false)}로 만들면 이 빈은 "타입만으로 고를 때의 후보"에서 빠져서,
 * 주 DB(MySQL)의 자동 설정·JPA·Flyway는 그대로 동작하고, 이 DB는 {@code @Qualifier("recommend")}로 이름을 짚어야만 주입된다.
 */
@Configuration
public class RecommendDataSourceConfig {

    public static final String QUALIFIER = "recommend";

    @Bean(defaultCandidate = false)
    @Qualifier(QUALIFIER)
    public HikariDataSource recommendDataSource(RecommendProperties properties) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(properties.datasource().url())
                .username(properties.datasource().username())
                .password(properties.datasource().password())
                .build();
        dataSource.setPoolName("recommend");
        return dataSource;
    }

    /**
     * 추천 DB의 표는 이 DB 전용 Flyway가 만든다(classpath:db/recommend). 주 DB의 Flyway(db/migration)와 이력 표가 따로다.
     * Flyway 빈을 하나 더 등록하면 주 DB의 Flyway 자동 설정과 부딪힐 수 있어서, 빈으로 두지 않고 여기서 바로 마이그레이션한다.
     */
    @Bean(defaultCandidate = false)
    @Qualifier(QUALIFIER)
    public NamedParameterJdbcTemplate recommendJdbcTemplate(@Qualifier(QUALIFIER) DataSource recommendDataSource) {
        Flyway.configure()
                .dataSource(recommendDataSource)
                .locations("classpath:db/recommend")
                .load()
                .migrate();
        return new NamedParameterJdbcTemplate(recommendDataSource);
    }

}
