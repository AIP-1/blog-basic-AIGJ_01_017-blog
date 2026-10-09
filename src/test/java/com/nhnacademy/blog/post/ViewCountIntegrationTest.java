package com.nhnacademy.blog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.web.VisitorKeys;
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
 * 조회 기록과 조회수 (T053, POST-09, spec US5 시나리오 5). 같은 조회자가 5분 안에 다시 열면 한 번만 센다.
 */
class ViewCountIntegrationTest extends IntegrationTestSupport {

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
    void sameViewerWithinFiveMinutesCountsOnce() throws Exception {
        Cookie[] member = testMembers.loginCookies(testMembers.create());

        view(post, member).andExpect(status().isNoContent());
        view(post, member).andExpect(status().isNoContent());
        assertThat(viewCount(post)).isEqualTo(1);
        assertThat(viewLogRows(post)).isEqualTo(1);

        // 5분이 지난 것으로 기록을 옮기면 다시 센다
        jdbcTemplate.update("UPDATE view_log SET viewed_at = viewed_at - INTERVAL 6 MINUTE WHERE post_id = ?",
                post.getId());
        view(post, member).andExpect(status().isNoContent());
        assertThat(viewCount(post)).isEqualTo(2);
        assertThat(viewLogRows(post)).isEqualTo(2);
    }

    @Test
    void anonymousViewerIsKnownByVisitorCookie() throws Exception {
        MockHttpServletResponse first = view(post, null).andExpect(status().isNoContent()).andReturn().getResponse();
        Cookie visitor = first.getCookie(VisitorKeys.COOKIE);
        assertThat(visitor).isNotNull();
        assertThat(first.getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "Domain=.blog.test");

        // 같은 쿠키로 다시 보면 세지 않고, 쿠키를 다시 주지도 않는다
        MockHttpServletResponse again = view(post, new Cookie[]{visitor}).andReturn().getResponse();
        assertThat(again.getCookie(VisitorKeys.COOKIE)).isNull();
        assertThat(viewCount(post)).isEqualTo(1);

        // 쿠키가 없거나 UUID가 아닌 값이면 다른 비회원이다
        view(post, null);
        view(post, new Cookie[]{new Cookie(VisitorKeys.COOKIE, "' OR 1=1 --")}).andExpect(status().isNoContent());
        assertThat(viewCount(post)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("SELECT viewer_key FROM view_log WHERE post_id = ?", String.class,
                post.getId())).allMatch(key -> key.matches("a:[0-9a-f-]{36}"));
    }

    @Test
    void invisiblePostIsNotFoundAndNotCounted() throws Exception {
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);
        Post blinded = testPosts.published(blog, Visibility.PUBLIC);
        testPosts.blind(blinded);
        Post deleted = testPosts.published(blog, Visibility.PUBLIC);
        testPosts.delete(deleted);

        for (Post hidden : List.of(privatePost, blinded, deleted)) {
            view(hidden, testMembers.loginCookies(testMembers.create())).andExpect(status().isNotFound());
            assertThat(viewCount(hidden)).isZero();
            assertThat(viewLogRows(hidden)).isZero();
        }
        mockMvc.perform(withHeaders(post("/api/posts/999999999/views"))).andExpect(status().isNotFound());

        // 주인은 비공개 글도 볼 수 있으므로 센다
        view(privatePost, testMembers.loginCookies(blog.getMember())).andExpect(status().isNoContent());
        assertThat(viewCount(privatePost)).isEqualTo(1);
    }

    @Test
    void concurrentRefreshesBySameViewerCountOnce() throws Exception {
        Cookie[] member = testMembers.loginCookies(testMembers.create());
        Cookie[] other = testMembers.loginCookies(testMembers.create());
        List<Callable<Integer>> refreshes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Cookie[] viewer = i % 2 == 0 ? member : other;
            refreshes.add(() -> view(post, viewer).andReturn().getResponse().getStatus());
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (Future<Integer> result : executor.invokeAll(refreshes)) {
                assertThat(result.get()).isEqualTo(204);
            }
        }

        assertThat(viewCount(post)).isEqualTo(2);
        assertThat(viewLogRows(post)).isEqualTo(2);
    }

    private ResultActions view(Post target, Cookie[] cookies) throws Exception {
        MockHttpServletRequestBuilder request = withHeaders(post("/api/posts/" + target.getId() + "/views"));
        if (cookies != null) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

    private MockHttpServletRequestBuilder withHeaders(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest");
    }

    private long viewCount(Post target) {
        return jdbcTemplate.queryForObject("SELECT view_count FROM post WHERE id = ?", Long.class, target.getId());
    }

    private long viewLogRows(Post target) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM view_log WHERE post_id = ?", Long.class,
                target.getId());
    }

}
