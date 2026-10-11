package com.nhnacademy.blog.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.nhnacademy.blog.ranking.application.BloggerRanking;
import com.nhnacademy.blog.home.application.PopularRanking;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

/** 인기 블로거·랭킹 전체보기·추천 블로그 (스텝 20: T078~T080, HOME-04·05, SUB-06, spec US16 5~7). */
class RankingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCache(BloggerRanking.CACHE).clear();
        cacheManager.getCache(PopularRanking.CACHE).clear();
    }

    @Test
    void bloggersAreRankedBySevenDayScoreAndHiddenBlogsAreLeftOut() throws Exception {
        Member a = testMembers.create();
        Member b = testMembers.create();
        Member c = testMembers.create();
        Member reader = testMembers.create();
        Blog blogA = testBlogs.createPrimary(a);
        Blog blogB = testBlogs.createPrimary(b);
        Blog blogC = testBlogs.createPrimary(c);
        long postA = publish(blogA, testMembers.loginCookies(a), "A의 글");
        long postB = publish(blogB, testMembers.loginCookies(b), "B의 글");
        publish(blogC, testMembers.loginCookies(c), "C의 글");
        // 지난 시간 칸 안의 활동: A는 공감 40점어치, B는 조회 1, C는 새 구독자 10000(뒤에서 이용 제한)
        for (int i = 0; i < 200; i++) {
            jdbcTemplate.update("INSERT INTO view_log (post_id, viewer_key, viewed_at) VALUES (?, ?, NOW() - INTERVAL 2 HOUR)",
                    postA, "a:" + i);
        }
        jdbcTemplate.update("INSERT INTO view_log (post_id, viewer_key, viewed_at) VALUES (?, 'x', NOW() - INTERVAL 2 HOUR)", postB);
        jdbcTemplate.update("INSERT INTO subscription (member_id, blog_id, created_at) VALUES (?, ?, NOW() - INTERVAL 2 HOUR)",
                reader.getId(), blogA.getId());
        for (int i = 0; i < 300; i++) {
            jdbcTemplate.update("INSERT INTO view_log (post_id, viewer_key, viewed_at) VALUES (?, ?, NOW() - INTERVAL 2 HOUR)",
                    postB, "c:" + i);
        }
        testBlogs.restrict(blogB);

        String home = send(get("/api/home/bloggers"), "blog.test", null, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> homeIds = JsonPath.read(home, "$.items[*].blog.id");
        assertThat(homeIds).hasSizeLessThanOrEqualTo(5);
        String all = send(get("/api/ranking/bloggers"), "blog.test", null, null)
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(all, "$.items[*].blog.id");
        assertThat(ids).contains(blogA.getId().intValue()).doesNotContain(blogB.getId().intValue());
        String snapshotAt = JsonPath.read(all, "$.snapshotAt");
        assertThat(snapshotAt).endsWith(":00:00+09:00");

        // 추천: 구독 중인 A는 빠진다
        String recommend = send(get("/api/recommend/blogs"), "blog.test", testMembers.loginCookies(reader), null)
                .andReturn().getResponse().getContentAsString();
        List<Integer> recommended = JsonPath.read(recommend, "$[*].blog.id");
        assertThat(recommended).doesNotContain(blogA.getId().intValue()).hasSizeLessThanOrEqualTo(5);
        // 내 블로그도 빠진다
        String mine = send(get("/api/recommend/blogs"), "blog.test", testMembers.loginCookies(a), null)
                .andReturn().getResponse().getContentAsString();
        assertThat((List<Integer>) JsonPath.read(mine, "$[*].blog.id")).doesNotContain(blogA.getId().intValue());

        // 같은 기준 시각이면 이어서, 다르면 409와 새 기준 시각
        send(get("/api/ranking/bloggers").param("snapshotAt", snapshotAt).param("offset", "20"), "blog.test", null, null)
                .andExpect(status().isOk());
        send(get("/api/ranking/bloggers").param("snapshotAt", "2020-01-01T00:00:00+09:00").param("offset", "20"),
                "blog.test", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RANKING_UPDATED"))
                .andExpect(jsonPath("$.detail.snapshotAt").value(snapshotAt));
        send(get("/api/ranking/bloggers").param("offset", "100"), "blog.test", null, null).andExpect(status().isBadRequest());
    }

    @Test
    void rankingPostsUseTheHomeSnapshot() throws Exception {
        Member owner = testMembers.create();
        Blog blog = testBlogs.createPrimary(owner);
        long post = publish(blog, testMembers.loginCookies(owner), "많이 본 글");
        for (int i = 0; i < 500; i++) {
            // DB의 NOW()는 컨테이너 시간대(UTC)라, 앱과 같은 한국 시간을 자바에서 넣는다
            jdbcTemplate.update("INSERT INTO view_log (post_id, viewer_key, viewed_at) VALUES (?, ?, ?)",
                    post, "p:" + i, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1));
        }
        String home = send(get("/api/home/popular"), "blog.test", null, null).andReturn().getResponse().getContentAsString();
        String ranking = send(get("/api/ranking/posts"), "blog.test", null, null)
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(ranking, "$.snapshotAt")).isEqualTo(JsonPath.read(home, "$.snapshotAt"));
        assertThat((Integer) JsonPath.read(ranking, "$.items[0].post.id")).isEqualTo((int) post);
        send(get("/api/ranking/posts").param("snapshotAt", "2020-01-01T00:00:00+09:00"), "blog.test", null, null)
                .andExpect(status().isConflict());
    }

    private long publish(Blog target, Cookie[] cookies, String title) throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                TestBlogs.host(target), cookies,
                "{\"title\":\"" + title + "\",\"contentHtml\":\"<p>본문</p>\",\"visibility\":\"PUBLIC\",\"status\":\"PUBLISHED\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String host, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, host).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
