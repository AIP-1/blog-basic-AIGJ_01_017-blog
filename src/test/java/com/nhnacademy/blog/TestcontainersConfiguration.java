package com.nhnacademy.blog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 MySQL 8, Redis, 추천 전용 PostgreSQL(pgvector). docker-compose.yml과 같은 이미지를 쓴다.
 * PostgreSQL은 주 DB가 아니라서 @ServiceConnection을 붙이지 않고(붙이면 주 DB 연결로 쓰인다),
 * 접속 정보를 app.recommend.datasource.* 설정으로 넣는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);
    }

    @Bean
    PostgreSQLContainer recommendPostgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("recommend");
    }

    @Bean
    DynamicPropertyRegistrar recommendDataSourceProperties(PostgreSQLContainer recommendPostgresContainer) {
        return registry -> {
            registry.add("app.recommend.datasource.url", recommendPostgresContainer::getJdbcUrl);
            registry.add("app.recommend.datasource.username", recommendPostgresContainer::getUsername);
            registry.add("app.recommend.datasource.password", recommendPostgresContainer::getPassword);
        };
    }

}
