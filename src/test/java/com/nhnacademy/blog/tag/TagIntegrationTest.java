package com.nhnacademy.blog.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 글에 태그 달기 (T038, TAG-01, spec US2 시나리오 11).
 */
class TagIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Blog blog;
    Cookie[] cookies;

    @BeforeEach
    void setUp() {
        Member owner = testMembers.create();
        blog = testBlogs.create(owner);
        cookies = testMembers.loginCookies(owner);
    }

    @Test
    void tagsAreCleanedDedupedAndCreatedOnce() throws Exception {
        long id = publish("[\"spring\", \" #Security \", \"Spring\", \"\", \"spring\"]");

        mockMvc.perform(get("/api/posts/" + id).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.tags", contains("Security", "spring")));
        assertThat(tagCount()).isEqualTo(2);

        // 다른 글에서 대소문자만 다르게 써도 같은 태그를 쓴다
        publish("[\"SPRING\", \"jpa\"]");
        assertThat(tagCount()).isEqualTo(3);
    }

    @Test
    void elevenTagsAreRejected() throws Exception {
        StringBuilder names = new StringBuilder("[");
        for (int i = 0; i < 11; i++) {
            names.append(i == 0 ? "" : ",").append("\"t").append(i).append('"');
        }
        send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), body(names + "]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_TAGS"));
        send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                body("[\"" + "가".repeat(31) + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tagNames"));
        assertThat(tagCount()).isZero();
    }

    @Test
    void editingReplacesTagsButKeepsBlogTags() throws Exception {
        long id = publish("[\"spring\", \"jpa\"]");

        send(put("/api/posts/" + id), body("[\"jpa\", \"mysql\"]")).andExpect(status().isOk());

        send(get("/api/manage/posts/" + id), null)
                .andExpect(jsonPath("$.tagNames", contains("jpa", "mysql")));
        assertThat(jdbcTemplate.queryForList("SELECT name FROM tag WHERE blog_id = ? ORDER BY name", String.class,
                blog.getId())).containsExactly("jpa", "mysql", "spring");
    }

    @Test
    void listsPostsByTagIgnoringCase() throws Exception {
        long springPost = publish("[\"spring\"]");
        long both = publish("[\"Spring\", \"jpa\"]");
        publish("[\"jpa\"]");
        long privatePost = publish("[\"spring\"]");
        send(patch("/api/posts/" + privatePost + "/visibility"), "{\"visibility\":\"PRIVATE\"}")
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts").param("tag", "SPRING").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains((int) both, (int) springPost)));
        mockMvc.perform(get("/api/posts").param("tag", "없는태그").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isNotFound());
    }

    private long publish(String tagNames) throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                body(tagNames)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private int tagCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag WHERE blog_id = ?", Integer.class, blog.getId());
    }

    private static String body(String tagNames) {
        return """
                {"title":"태그 글","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"PUBLISHED","tagNames":%s}
                """.formatted(tagNames);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest")
                .cookie(cookies);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

}
