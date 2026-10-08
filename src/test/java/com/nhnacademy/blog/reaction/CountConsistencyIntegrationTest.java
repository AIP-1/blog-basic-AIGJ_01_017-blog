package com.nhnacademy.blog.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * 공감·댓글 수 맞추기 (T049, SOC-01, CMT-01, spec US3 시나리오 6·8).
 * 비정규화한 like_count·comment_count가 여러 사람이 동시에 누르고 지워도 실제 행 수와 같은지 본다.
 */
class CountConsistencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Blog blog;
    Post post;

    @BeforeEach
    void setUp() {
        blog = testBlogs.create(testMembers.create());
        post = testPosts.published(blog, Visibility.PUBLIC);
    }

    @Test
    void anonymousCannotLikeOrComment() throws Exception {
        send(put("/api/posts/" + post.getId() + "/like"), null, null).andExpect(status().isUnauthorized());
        send(post("/api/posts/" + post.getId() + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()),
                null, "{\"content\":\"비회원\"}").andExpect(status().isUnauthorized());
        assertCountsMatchRows();
    }

    @Test
    void likeCountMatchesRowsUnderConcurrentToggling() throws Exception {
        List<Cookie[]> members = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            members.add(testMembers.loginCookies(testMembers.create()));
        }
        // 다섯 명이 각자 켜기·끄기를 섞어 동시에 여러 번 누른다
        List<Callable<Integer>> clicks = new ArrayList<>();
        for (int round = 0; round < 6; round++) {
            for (int i = 0; i < members.size(); i++) {
                Cookie[] member = members.get(i);
                MockHttpServletRequestBuilder request = (round + i) % 2 == 0
                        ? put("/api/posts/" + post.getId() + "/like")
                        : delete("/api/posts/" + post.getId() + "/like");
                clicks.add(() -> send(request, member, null).andReturn().getResponse().getStatus());
            }
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (Future<Integer> result : executor.invokeAll(clicks)) {
                assertThat(result.get()).isEqualTo(200);
            }
        }

        assertCountsMatchRows();
        int rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?", Integer.class,
                post.getId());
        send(get("/api/posts/" + post.getId()), null, null).andExpect(jsonPath("$.likeCount").value(rows));
    }

    @Test
    void commentCountMatchesRowsAfterRepliesAndDeletes() throws Exception {
        Cookie[] a = testMembers.loginCookies(testMembers.create());
        Cookie[] b = testMembers.loginCookies(testMembers.create());
        long parent = comment(a, "부모", null);
        comment(b, "답글", parent);
        long other = comment(b, "다른 댓글", null);
        comment(a, "또 다른 댓글", null);

        send(delete("/api/comments/" + parent), a, null).andExpect(status().isNoContent());
        send(delete("/api/comments/" + other), b, null).andExpect(status().isNoContent());
        // 지운 댓글을 다시 지워도 수가 두 번 줄지 않는다
        send(delete("/api/comments/" + other), b, null).andExpect(status().isNotFound());

        assertCountsMatchRows();
        send(get("/api/posts/" + post.getId()), null, null).andExpect(jsonPath("$.commentCount").value(2));
        send(get("/api/posts/" + post.getId() + "/comments"), null, null)
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    private void assertCountsMatchRows() {
        assertThat(jdbcTemplate.queryForObject("SELECT like_count FROM post WHERE id = ?", Integer.class, post.getId()))
                .isEqualTo(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?",
                        Integer.class, post.getId()));
        assertThat(jdbcTemplate.queryForObject("SELECT comment_count FROM post WHERE id = ?", Integer.class,
                post.getId()))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM comment WHERE post_id = ? AND deleted_at IS NULL", Integer.class,
                        post.getId()));
    }

    private long comment(Cookie[] cookies, String content, Long parentId) throws Exception {
        String body = parentId == null ? "{\"content\":\"" + content + "\"}"
                : "{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}";
        String response = send(post("/api/posts/" + post.getId() + "/comments")
                        .header("Idempotency-Key", UUID.randomUUID().toString()), cookies, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie[] cookies, String body) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (cookies != null) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

}
