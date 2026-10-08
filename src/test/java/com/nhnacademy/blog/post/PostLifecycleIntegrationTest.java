package com.nhnacademy.blog.post;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * 글의 한살이를 API로만 돌린다 (T040, spec US2 수용 시나리오 4·7·9).
 * 수정해도 주소·순서가 그대로, 카테고리를 지우면 미분류, 비공개로 바꾸면 남의 목록·글 수·사이드바에서 빠진다.
 */
class PostLifecycleIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Blog blog;
    Cookie[] ownerCookies;

    @BeforeEach
    void setUp() {
        Member owner = testMembers.create();
        blog = testBlogs.create(owner);
        ownerCookies = testMembers.loginCookies(owner);
    }

    @Test
    void editingKeepsAddressAndOrder() throws Exception {
        long first = publish("첫 글", null);
        long second = publish("둘째 글", null);
        long third = publish("셋째 글", null);
        // 같은 초에 발행돼도 순서가 분명하게 발행 시각을 벌린다
        spreadPublishedAt(first, second, third);

        send(put("/api/posts/" + first), body("첫 글 (고침)", null, "PUBLIC")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first));

        list(null).andExpect(jsonPath("$.content[*].id", contains((int) third, (int) second, (int) first)))
                .andExpect(jsonPath("$.content[2].title").value("첫 글 (고침)"));
    }

    @Test
    void deletingCategoryMovesItsPostsToUncategorized() throws Exception {
        Number categoryId = JsonPath.read(send(post("/api/categories"), "{\"name\":\"A\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        publish("A의 글 1", categoryId.longValue());
        publish("A의 글 2", categoryId.longValue());

        send(delete("/api/categories/" + categoryId), null).andExpect(status().isNoContent());

        list("?categoryId=0").andExpect(jsonPath("$.totalElements").value(2));
        list("").andExpect(jsonPath("$.content[0].category").doesNotExist());
        mockMvc.perform(get("/api/blog/sidebar").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.modules[1].data.uncategorizedCount").value(2))
                .andExpect(jsonPath("$.modules[1].data.categories").isEmpty());
        list("?categoryId=" + categoryId).andExpect(status().isNotFound());
    }

    @Test
    void privatePostLeavesListsCountsAndSidebarForOthers() throws Exception {
        long kept = publish("공개로 둘 글", null);
        long hidden = publish("비공개로 바꿀 글", null);

        send(patch("/api/posts/" + hidden + "/visibility"), "{\"visibility\":\"PRIVATE\"}")
                .andExpect(status().isNoContent());

        list(null).andExpect(jsonPath("$.content[*].id", contains((int) kept)));
        mockMvc.perform(get("/api/blog").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.postCount").value(1));
        mockMvc.perform(get("/api/blog/sidebar").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.modules[1].data.totalCount").value(1))
                .andExpect(jsonPath("$.modules[2].data[*].id", contains((int) kept)));
        // 주인에게는 그대로 보인다
        mockMvc.perform(get("/api/posts").header(HttpHeaders.HOST, TestBlogs.host(blog)).cookie(ownerCookies))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private long publish(String title, Long categoryId) throws Exception {
        String response = mockMvc.perform(withDefaults(post("/api/posts"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(body(title, categoryId, "PUBLIC")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void spreadPublishedAt(long... ids) {
        for (int i = 0; i < ids.length; i++) {
            jdbcTemplate.update("UPDATE post SET published_at = ? WHERE id = ?",
                    "2026-10-0" + (i + 1) + " 09:00:00", ids[i]);
        }
    }

    private String body(String title, Long categoryId, String visibility) {
        return """
                {"title":"%s","contentHtml":"<p>본문</p>","categoryId":%s,"visibility":"%s","status":"PUBLISHED"}
                """.formatted(title, categoryId, visibility);
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/posts" + (query == null ? "" : query))
                .header(HttpHeaders.HOST, TestBlogs.host(blog)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        withDefaults(request);
        if (body != null) {
            request.content(body);
        }
        return mockMvc.perform(request);
    }

    private MockHttpServletRequestBuilder withDefaults(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.HOST, TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(ownerCookies);
    }

}
