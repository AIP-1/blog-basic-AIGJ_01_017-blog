package com.nhnacademy.blog.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.recommend.application.PostEmbeddingService;
import com.nhnacademy.blog.recommend.config.RecommendDataSourceConfig;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 글 임베딩 저장 (T069b, R-02). 발행·수정이 커밋된 뒤 비동기로 PostgreSQL post_embedding에 넣고, 지우면 지운다.
 * 비동기라 결과가 날 때까지 잠깐 기다린다(await).
 */
class PostEmbeddingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    PostEmbeddingService postEmbeddingService;

    @Autowired
    @Qualifier(RecommendDataSourceConfig.QUALIFIER)
    NamedParameterJdbcTemplate recommendJdbc;

    Blog blog;
    Cookie[] owner;

    @BeforeEach
    void setUp() {
        Member member = testMembers.create();
        blog = testBlogs.create(member);
        owner = testMembers.loginCookies(member);
    }

    @Test
    void publishEditAndDeleteKeepTheEmbeddingInStep() throws Exception {
        long id = publish("스프링 시큐리티 정리", "필터 체인과 인증");
        await(() -> embedding(id) != null);
        String first = embedding(id);
        assertThat(first).startsWith("[").contains(",");

        send(put("/api/posts/" + id), body("제주 여행", "바다와 맛집")).andExpect(status().isOk());
        await(() -> !first.equals(embedding(id)));

        send(delete("/api/posts/" + id), null).andExpect(status().isNoContent());
        await(() -> embedding(id) == null);
    }

    @Test
    void syncFillsMissingEmbeddingsAndDropsDeletedOnes() throws Exception {
        long id = publish("동기화 글", "본문");
        await(() -> embedding(id) != null);
        recommendJdbc.update("DELETE FROM post_embedding WHERE post_id = :id", Map.of("id", id));
        recommendJdbc.update("INSERT INTO post_embedding (post_id, embedding) VALUES (:id, array_fill(0.1, ARRAY[1024])::vector)",
                Map.of("id", 999_999_999L));

        assertThat(postEmbeddingService.sync()).isGreaterThanOrEqualTo(1);

        assertThat(embedding(id)).isNotNull();
        assertThat(embedding(999_999_999L)).isNull();
    }

    private long publish(String title, String text) throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                body(title, text)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private String embedding(long postId) {
        return recommendJdbc.query("SELECT embedding::text FROM post_embedding WHERE post_id = :id",
                Map.of("id", postId), rs -> rs.next() ? rs.getString(1) : null);
    }

    /** 비동기 작업이 끝날 때까지 5초까지 기다린다. */
    static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("5초 안에 조건이 맞지 않았습니다");
            }
            Thread.sleep(50);
        }
    }

    static String body(String title, String text) {
        return """
                {"title":"%s","contentHtml":"<p>%s</p>","visibility":"PUBLIC","status":"PUBLISHED"}
                """.formatted(title, text);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest")
                .cookie(owner);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

}
