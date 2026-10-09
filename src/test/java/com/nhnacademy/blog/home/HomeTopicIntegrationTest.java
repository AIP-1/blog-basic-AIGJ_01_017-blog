package com.nhnacademy.blog.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.home.application.PopularRanking;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 홈 주제별 글 (T064, HOME-03, spec US5 시나리오 7). 인기 점수 순 6개, 모자라면 그 주제의 최신 글로 채운다.
 * 다른 테스트가 쓰지 않는 주제(HEALTH)를 쓴다. 채우는 글은 인기 글보다 나중 시각에 둔다.
 * 먼 미래 시각은 쓰지 않는다(홈 최신 글 테스트가 미래 시각 글을 쓰므로 섞인다).
 */
class HomeTopicIntegrationTest extends IntegrationTestSupport {

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

    @Autowired
    CacheManager cacheManager;

    Blog blog;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(cacheManager.getCache(PopularRanking.TOPIC_CACHE)).clear();
        blog = testBlogs.create(testMembers.create());
    }

    @Test
    void popularPostsOfTheTopicFirstThenLatestToMakeSix() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Post first = health(now.minusDays(3));
        views(first, 300, now);
        Post second = health(now.minusDays(3));
        views(second, 200, now);
        Post third = health(now.minusDays(3));
        views(third, 100, now);

        List<Long> latest = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            latest.add(0, health(now.minusHours(5).plusMinutes(i)).getId());
        }

        // 다른 주제, 주제 없음, 비공개 글은 활동이 많아도 빠진다
        Post travel = testPosts.published(blog, null, Visibility.PUBLIC, now);
        topic(travel, "TRAVEL");
        views(travel, 1000, now);
        views(testPosts.published(blog, Visibility.PUBLIC), 1000, now);
        Post privatePost = health(now.minusHours(1));
        jdbcTemplate.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", privatePost.getId());
        views(privatePost, 1000, now);

        String body = mockMvc.perform(get("/api/home/topics/HEALTH").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).map(Number::longValue).containsExactly(first.getId(), second.getId(), third.getId(),
                latest.get(0), latest.get(1), latest.get(2));
        assertThat((List<String>) JsonPath.read(body, "$[*].topic")).containsOnly("HEALTH");
        assertThat((String) JsonPath.read(body, "$[0].blog.address")).isEqualTo(blog.getAddress());
    }

    @Test
    void unknownTopicIsNotFound() throws Exception {
        mockMvc.perform(get("/api/home/topics/SPORTS").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/home/topics/health").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isNotFound());
    }

    private Post health(LocalDateTime publishedAt) {
        Post post = testPosts.published(blog, null, Visibility.PUBLIC, publishedAt);
        topic(post, "HEALTH");
        return post;
    }

    private void topic(Post post, String topic) {
        jdbcTemplate.update("UPDATE post SET topic = ? WHERE id = ?", topic, post.getId());
    }

    private void views(Post post, int count, LocalDateTime at) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{post.getId(), "t:" + UUID.randomUUID(), at});
        }
        jdbcTemplate.batchUpdate("INSERT INTO view_log (post_id, viewer_key, viewed_at) VALUES (?, ?, ?)", rows);
    }

}
