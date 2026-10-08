package com.nhnacademy.blog.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 블로그 안 검색 (T047, SRCH-01, research R-16, spec US3 시나리오 5).
 */
class SearchIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    Blog blog;
    Cookie[] owner;
    long security;
    long jpa;
    long docker;

    @BeforeEach
    void setUp() throws Exception {
        Member member = testMembers.create();
        blog = testBlogs.create(member);
        owner = testMembers.loginCookies(member);
        security = publish("Spring Security 정리", "<p>필터 체인</p>", "[]", "PUBLIC");
        jpa = publish("두 번째 글", "<p><strong>JPA</strong> 정리 &amp; 팁</p>", "[]", "PUBLIC");
        docker = publish("세 번째 글", "<p>컨테이너</p>", "[\"Docker\"]", "PUBLIC");
    }

    @Test
    void findsInTitleTextAndTagIgnoringCase() throws Exception {
        search("SECURITY", null).andExpect(jsonPath("$.content[*].id", contains((int) security)));
        search("jpa", null).andExpect(jsonPath("$.content[*].id", contains((int) jpa)));
        search("docker", null).andExpect(jsonPath("$.content[*].id", contains((int) docker)));
        // 같은 글이 제목·본문 둘 다 맞아도 한 번만 나온다, 최신순
        search("정리", null).andExpect(jsonPath("$.content[*].id", contains((int) jpa, (int) security)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void htmlTagNamesAndEntitiesDoNotMatch() throws Exception {
        search("strong", null).andExpect(jsonPath("$.content", empty()));
        search("amp", null).andExpect(jsonPath("$.content", empty()));
        // 문자 참조는 글자로 풀려 저장되어 & 로 찾힌다
        search("& 팁", null).andExpect(jsonPath("$.content[*].id", contains((int) jpa)));
    }

    @Test
    void likeWildcardsAreLiteral() throws Exception {
        long sale = publish("할인 100% 후기", "<p>x</p>", "[]", "PUBLIC");
        publish("1000원짜리", "<p>x</p>", "[]", "PUBLIC");
        publish("a_b 표기", "<p>x</p>", "[]", "PUBLIC");

        search("100%", null).andExpect(jsonPath("$.content[*].id", contains((int) sale)));
        search("_", null).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyVisiblePostsAreFound() throws Exception {
        long hidden = publish("비공개 Security 메모", "<p>x</p>", "[]", "PRIVATE");

        search("security", null).andExpect(jsonPath("$.content[*].id", contains((int) security)));
        search("security", owner).andExpect(jsonPath("$.content[*].id", contains((int) hidden, (int) security)));
    }

    @Test
    void blankOrTooLongQueryIs400() throws Exception {
        search("   ", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        mockMvc.perform(get("/api/search").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isBadRequest());
        search("가".repeat(101), null).andExpect(status().isBadRequest());
    }

    private ResultActions search(String q, Cookie[] cookies) throws Exception {
        var request = get("/api/search").param("q", q).header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

    private long publish(String title, String html, String tags, String visibility) throws Exception {
        String body = """
                {"title":"%s","contentHtml":"%s","visibility":"%s","status":"PUBLISHED","tagNames":%s}
                """.formatted(title, html.replace("\"", "\\\""), visibility, tags);
        String response = mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.HOST, TestBlogs.host(blog))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

}
