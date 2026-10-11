package com.nhnacademy.blog.manage;

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
import com.nhnacademy.blog.manage.application.StatsAggregator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 방문 통계 (스텝 20: T082·T083, MNG-03, spec US13 1). */
class StatsIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StatsAggregator statsAggregator;

    @Test
    void visitsAreCountedOncePerDayExceptOwnerAndAggregatedAtNight() throws Exception {
        Member owner = testMembers.create();
        Member reader = testMembers.create();
        Blog blog = testBlogs.createPrimary(owner);
        Cookie[] ownerCookies = testMembers.loginCookies(owner);
        Cookie[] readerCookies = testMembers.loginCookies(reader);
        long post = publish(blog, ownerCookies, "통계 글");
        String host = TestBlogs.host(blog);

        // 화면 요청이 방문이다. 같은 회원은 하루 한 번, 주인 본인은 세지 않는다. Referer로 유입 종류
        mockMvc.perform(get("/").header(HttpHeaders.HOST, host).header(HttpHeaders.REFERER, "https://www.google.com/search?q=x")
                .cookie(readerCookies)).andExpect(status().isOk());
        mockMvc.perform(get("/" + post).header(HttpHeaders.HOST, host).cookie(readerCookies)).andExpect(status().isOk());
        mockMvc.perform(get("/").header(HttpHeaders.HOST, host).cookie(ownerCookies)).andExpect(status().isOk());
        mockMvc.perform(get("/manage").header(HttpHeaders.HOST, host).cookie(ownerCookies)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForList("SELECT referrer_type FROM blog_visit WHERE blog_id = ?", String.class,
                blog.getId())).containsExactly("SEARCH");

        send(get("/api/manage/stats"), host, ownerCookies, null)
                .andExpect(jsonPath("$.today").value(1))
                .andExpect(jsonPath("$.recentPosts[0].title").value("통계 글"));
        send(get("/api/manage/stats"), host, readerCookies, null).andExpect(status().isForbidden());

        // 어제 방문 둘(직접·SNS)과 조회 하나를 넣고 새벽 작업을 돌린다
        LocalDate yesterday = LocalDate.now().minusDays(1);
        jdbcTemplate.update("INSERT INTO blog_visit (blog_id, visit_date, visitor_key, referrer_type, referrer_host) VALUES (?, ?, 'a:1', 'DIRECT', '')",
                blog.getId(), yesterday);
        jdbcTemplate.update("INSERT INTO blog_visit (blog_id, visit_date, visitor_key, referrer_type, referrer_host) VALUES (?, ?, 'a:2', 'SNS', 'x.com')",
                blog.getId(), yesterday);
        jdbcTemplate.update("INSERT INTO view_log (post_id, viewer_key, viewed_at) VALUES (?, 'a:1', ?)", post,
                yesterday.atTime(12, 0));
        jdbcTemplate.update("INSERT INTO blog_visit (blog_id, visit_date, visitor_key, referrer_type, referrer_host) VALUES (?, ?, 'a:old', 'DIRECT', '')",
                blog.getId(), LocalDate.now().minusDays(10));
        statsAggregator.run();
        statsAggregator.run();

        assertThat(jdbcTemplate.queryForObject("SELECT visitor_count FROM blog_daily_stat WHERE blog_id = ? AND stat_date = ?",
                Integer.class, blog.getId(), yesterday)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT total_visitor_count FROM blog WHERE id = ?", Long.class,
                blog.getId())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM blog_visit WHERE blog_id = ? AND visitor_key = 'a:old'",
                Integer.class, blog.getId())).isZero();

        send(get("/api/manage/stats"), host, ownerCookies, null)
                .andExpect(jsonPath("$.yesterday").value(2))
                .andExpect(jsonPath("$.total").value(3));
        send(get("/api/manage/stats/visitors"), host, ownerCookies, null)
                .andExpect(jsonPath("$", hasSize(14)))
                .andExpect(jsonPath("$[12].date").value(yesterday.toString()))
                .andExpect(jsonPath("$[12].visitorCount").value(2))
                .andExpect(jsonPath("$[12].viewCount").value(1))
                .andExpect(jsonPath("$[13].visitorCount").value(1));
        send(get("/api/manage/stats/visitors").param("unit", "month"), host, ownerCookies, null)
                .andExpect(jsonPath("$", hasSize(12)));
        send(get("/api/manage/stats/visitors").param("unit", "year"), host, ownerCookies, null)
                .andExpect(status().isBadRequest());
        send(get("/api/manage/stats/referrers"), host, ownerCookies, null)
                .andExpect(jsonPath("$.byType[*].type", org.hamcrest.Matchers.containsInAnyOrder("SEARCH", "DIRECT", "SNS")))
                .andExpect(jsonPath("$.bySite[?(@.host == 'google.com')].count", contains(1)));
        send(get("/api/manage/stats/popular-posts").param("range", "7d"), host, ownerCookies, null)
                .andExpect(jsonPath("$[0].post.id").value(post))
                .andExpect(jsonPath("$[0].rank").value(1));
        send(get("/api/manage/stats/popular-posts"), host, ownerCookies, null)
                .andExpect(jsonPath("$[0].post.title").value("통계 글"));
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
