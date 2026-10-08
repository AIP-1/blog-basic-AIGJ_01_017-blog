package com.nhnacademy.blog.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 공감 (T046, SOC-01, spec US3 시나리오 8).
 */
class LikeIntegrationTest extends IntegrationTestSupport {

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
    Cookie[] reader;

    @BeforeEach
    void setUp() {
        blog = testBlogs.create(testMembers.create());
        post = testPosts.published(blog, Visibility.PUBLIC);
        reader = testMembers.loginCookies(testMembers.create());
    }

    @Test
    void likeAndUnlikeAreIdempotent() throws Exception {
        send(put("/api/posts/" + post.getId() + "/like"), reader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        send(put("/api/posts/" + post.getId() + "/like"), reader).andExpect(jsonPath("$.likeCount").value(1));

        send(get("/api/posts/" + post.getId()), reader)
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.viewer.liked").value(true))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        send(delete("/api/posts/" + post.getId() + "/like"), reader)
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
        send(delete("/api/posts/" + post.getId() + "/like"), reader).andExpect(jsonPath("$.likeCount").value(0));
        send(get("/api/posts/" + post.getId()), reader).andExpect(jsonPath("$.viewer.liked").value(false));
    }

    @Test
    void manyMembersAddUp() throws Exception {
        for (int i = 0; i < 3; i++) {
            send(put("/api/posts/" + post.getId() + "/like"), testMembers.loginCookies(testMembers.create()))
                    .andExpect(status().isOk());
        }
        send(put("/api/posts/" + post.getId() + "/like"), reader).andExpect(jsonPath("$.likeCount").value(4));
    }

    @Test
    void rapidConcurrentClicksCountOnce() throws Exception {
        List<Callable<MockHttpServletResponse>> clicks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            clicks.add(() -> send(put("/api/posts/" + post.getId() + "/like"), reader).andReturn().getResponse());
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (Future<MockHttpServletResponse> result : executor.invokeAll(clicks)) {
                assertThat(result.get().getStatus()).isEqualTo(200);
                // 늦게 처리된 요청도 앞 요청이 커밋한 수를 돌려줘야 한다(옛 스냅샷의 0이 아니라)
                assertThat(result.get().getContentAsString()).contains("\"likeCount\":1");
            }
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?", Integer.class,
                post.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT like_count FROM post WHERE id = ?", Integer.class,
                post.getId())).isEqualTo(1);
    }

    @Test
    void anonymousIs401ButHiddenPostIs404() throws Exception {
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);

        send(put("/api/posts/" + post.getId() + "/like"), null).andExpect(status().isUnauthorized());
        send(put("/api/posts/" + privatePost.getId() + "/like"), null).andExpect(status().isNotFound());
        send(put("/api/posts/" + privatePost.getId() + "/like"), reader).andExpect(status().isNotFound());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie[] cookies) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest");
        if (cookies != null) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

}
