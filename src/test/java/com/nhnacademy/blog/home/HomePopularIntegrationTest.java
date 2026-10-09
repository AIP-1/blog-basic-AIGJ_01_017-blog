package com.nhnacademy.blog.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.home.application.PopularRanking;
import com.nhnacademy.blog.member.domain.Member;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 홈 인기 글 (T063, HOME-02, spec US5 시나리오 6). 최근 1시간 조회×1 + 공감×3 + 댓글×5 순 공개 글 10개, 5분 캐시.
 * 활동 행은 SQL로 바로 넣는다. 테스트끼리 DB를 같이 쓰므로 다른 테스트의 글이 순위에 섞일 수 있어,
 * 몇 위인지가 아니라 서로의 앞뒤와 빠졌는지를 본다.
 */
class HomePopularIntegrationTest extends IntegrationTestSupport {

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
        Objects.requireNonNull(cacheManager.getCache(PopularRanking.CACHE)).clear();
        blog = testBlogs.create(testMembers.create());
    }

    @Test
    void likesAndCommentsWeighMoreThanViewsAndOnlyRecentPublicActivityCounts() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // 조회만 400 → 400점
        Post viewsOnly = testPosts.published(blog, Visibility.PUBLIC);
        views(viewsOnly, 400, now);
        // 조회 300 + 공감 20×3 + 댓글 10×5 = 410점. 조회는 적어도 공감·댓글이 많아 위에 온다
        Post engaged = testPosts.published(blog, Visibility.PUBLIC);
        views(engaged, 300, now);
        likes(engaged, 20, now);
        comments(engaged, 10, now, false);

        // 빠져야 하는 글: 1시간보다 오래된 활동, 비공개 글, 이용 제한 블로그의 글, 지운 댓글만 많은 글
        Post old = testPosts.published(blog, Visibility.PUBLIC);
        views(old, 1000, now.minusHours(2));
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);
        views(privatePost, 1000, now);
        Blog restricted = testBlogs.create(testMembers.create());
        Post restrictedPost = testPosts.published(restricted, Visibility.PUBLIC);
        views(restrictedPost, 1000, now);
        testBlogs.restrict(restricted);
        Post deletedComments = testPosts.published(blog, Visibility.PUBLIC);
        comments(deletedComments, 200, now, true);

        String body = popular()
                .andExpect(jsonPath("$.snapshotAt").isString())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = ids(body);
        assertThat(ids).hasSizeLessThanOrEqualTo(10)
                .contains(engaged.getId(), viewsOnly.getId())
                .doesNotContain(old.getId(), privatePost.getId(), restrictedPost.getId(), deletedComments.getId());
        assertThat(ids.indexOf(engaged.getId())).isLessThan(ids.indexOf(viewsOnly.getId()));
        int engagedIndex = ids.indexOf(engaged.getId());
        assertThat((Integer) JsonPath.read(body, "$.items[" + engagedIndex + "].rank")).isEqualTo(engagedIndex + 1);
        assertThat((String) JsonPath.read(body, "$.items[" + engagedIndex + "].post.blog.address"))
                .isEqualTo(blog.getAddress());
    }

    @Test
    void rankingIsCachedButPostsThatBecameHiddenDropOutAtOnce() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Post first = testPosts.published(blog, Visibility.PUBLIC);
        views(first, 500, now);
        Post second = testPosts.published(blog, Visibility.PUBLIC);
        views(second, 450, now);

        String before = popular().andReturn().getResponse().getContentAsString();
        List<Long> beforeIds = ids(before);
        int firstIndex = beforeIds.indexOf(first.getId());
        assertThat(firstIndex).isNotNegative().isLessThan(beforeIds.indexOf(second.getId()));

        // 5분 안에는 점수가 바뀌어도 같은 순위·같은 기준 시각이다
        views(second, 100, now);
        String cached = popular().andReturn().getResponse().getContentAsString();
        assertThat(ids(cached)).isEqualTo(beforeIds);
        assertThat((String) JsonPath.read(cached, "$.snapshotAt")).isEqualTo(JsonPath.read(before, "$.snapshotAt"));

        // 캐시 안의 글이라도 비공개가 되면 바로 빠지고, 다음 글이 그 자리로 올라온다(순위 번호도 이어진다)
        jdbcTemplate.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", first.getId());
        String afterHidden = popular().andReturn().getResponse().getContentAsString();
        List<Long> hiddenIds = ids(afterHidden);
        assertThat(hiddenIds).doesNotContain(first.getId());
        assertThat(hiddenIds.indexOf(second.getId())).isEqualTo(firstIndex);
        assertThat((Integer) JsonPath.read(afterHidden, "$.items[" + firstIndex + "].rank")).isEqualTo(firstIndex + 1);

        // 캐시가 비면(5분이 지나면) 새로 계산한다
        jdbcTemplate.update("UPDATE post SET visibility = 'PUBLIC' WHERE id = ?", first.getId());
        Objects.requireNonNull(cacheManager.getCache(PopularRanking.CACHE)).clear();
        List<Long> recomputed = ids(popular().andReturn().getResponse().getContentAsString());
        assertThat(recomputed.indexOf(second.getId())).isLessThan(recomputed.indexOf(first.getId()));
    }

    private static List<Long> ids(String body) {
        List<Number> ids = JsonPath.read(body, "$.items[*].post.id");
        return ids.stream().map(Number::longValue).toList();
    }

    private ResultActions popular() throws Exception {
        return mockMvc.perform(get("/api/home/popular").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isOk());
    }

    private void views(Post post, int count, LocalDateTime at) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{post.getId(), "t:" + UUID.randomUUID(), at});
        }
        jdbcTemplate.batchUpdate("INSERT INTO view_log (post_id, viewer_key, viewed_at) VALUES (?, ?, ?)", rows);
    }

    private void likes(Post post, int count, LocalDateTime at) {
        for (int i = 0; i < count; i++) {
            Member member = testMembers.create();
            jdbcTemplate.update("INSERT INTO post_like (post_id, member_id, created_at) VALUES (?, ?, ?)",
                    post.getId(), member.getId(), at);
        }
    }

    private void comments(Post post, int count, LocalDateTime at, boolean deleted) {
        Member member = testMembers.create();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{post.getId(), member.getId(), at, deleted ? at : null});
        }
        jdbcTemplate.batchUpdate("INSERT INTO comment (post_id, member_id, content, created_at, deleted_at) "
                + "VALUES (?, ?, '댓글', ?, ?)", rows);
    }

}
