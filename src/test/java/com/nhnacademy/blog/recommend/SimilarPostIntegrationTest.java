package com.nhnacademy.blog.recommend;

import static com.nhnacademy.blog.recommend.PostEmbeddingIntegrationTest.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.recommend.config.RecommendDataSourceConfig;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비슷한 글 API (T069c, OWN-06). 가까운 순, 다른 블로그 포함, 볼 수 없는 글과 자기 글은 빠짐.
 * 임베딩은 가짜(낱말이 많이 겹칠수록 가까움, TestRecommendConfiguration). 낱말에 테스트마다 다른 표식을 붙여
 * 다른 테스트의 글과 섞이지 않게 한다.
 */
class SimilarPostIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    @Qualifier(RecommendDataSourceConfig.QUALIFIER)
    NamedParameterJdbcTemplate recommendJdbc;

    Blog blog;
    Cookie[] owner;
    Blog otherBlog;
    Cookie[] otherOwner;
    String mark;

    @BeforeEach
    void setUp() {
        Member member = testMembers.create();
        blog = testBlogs.create(member);
        owner = testMembers.loginCookies(member);
        Member other = testMembers.create();
        otherBlog = testBlogs.create(other);
        otherOwner = testMembers.loginCookies(other);
        mark = "m" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void closestVisiblePostsFirstIncludingOtherBlogs() throws Exception {
        long base = publish(blog, owner, "스프링 시큐리티 필터 체인 인증 " + mark, "PUBLIC");
        long sameBlog = publish(blog, owner, "스프링 시큐리티 필터 인증 " + mark, "PUBLIC");
        long otherBlogPost = publish(otherBlog, otherOwner, "스프링 시큐리티 필터 " + mark, "PUBLIC");
        long far = publish(otherBlog, otherOwner, "제주 바다 여행 " + mark, "PUBLIC");
        long privateClose = publish(blog, owner, "스프링 시큐리티 필터 체인 인증 정리 " + mark, "PRIVATE");
        for (long id : new long[] {base, sameBlog, otherBlogPost, far, privateClose}) {
            await(() -> hasEmbedding(id));
        }

        String body = similar(base, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> ids = ids(body);
        // 비공개 글은 가장 비슷해도 다른 사람에게 나오지 않고, 자기 글도 빠진다
        assertThat(ids).doesNotContain(base, privateClose)
                .contains(sameBlog, otherBlogPost, far);
        assertThat(ids.indexOf(sameBlog)).isLessThan(ids.indexOf(far));
        assertThat(ids.indexOf(otherBlogPost)).isLessThan(ids.indexOf(far));
        int otherIndex = ids.indexOf(otherBlogPost);
        assertThat((String) JsonPath.read(body, "$[" + otherIndex + "].blog.address")).isEqualTo(otherBlog.getAddress());

        similar(base, "1").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void sizeMustBeOneToTen() throws Exception {
        long base = publish(blog, owner, "크기 " + mark, "PUBLIC");
        similar(base, "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        similar(base, "11").andExpect(status().isBadRequest());
    }

    @Test
    void invisibleSourcePostIsNotFoundAndMissingEmbeddingGivesEmptyList() throws Exception {
        long privatePost = publish(blog, owner, "비공개 원본 " + mark, "PRIVATE");
        similar(privatePost, null).andExpect(status().isNotFound());

        long post = publish(blog, owner, "임베딩 없음 " + mark, "PUBLIC");
        await(() -> hasEmbedding(post));
        recommendJdbc.update("DELETE FROM post_embedding WHERE post_id = :id", Map.of("id", post));
        similar(post, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    private long publish(Blog target, Cookie[] cookies, String title, String visibility) throws Exception {
        String response = mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.HOST, TestBlogs.host(target))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","contentHtml":"<p>%s</p>","visibility":"%s","status":"PUBLISHED"}
                                """.formatted(title, title, visibility))
                        .cookie(cookies))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions similar(long postId, String size) throws Exception {
        var request = get("/api/posts/" + postId + "/similar").header(HttpHeaders.HOST, TestBlogs.host(blog));
        if (size != null) {
            request.param("size", size);
        }
        return mockMvc.perform(request);
    }

    private boolean hasEmbedding(long postId) {
        return Boolean.TRUE.equals(recommendJdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM post_embedding WHERE post_id = :id)", Map.of("id", postId), Boolean.class));
    }

    private static List<Long> ids(String body) {
        List<Number> ids = JsonPath.read(body, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

}
