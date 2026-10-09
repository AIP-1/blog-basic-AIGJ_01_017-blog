package com.nhnacademy.blog.post;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 글 주제 (T056, POST-11). 고정 10개 중 하나를 고르거나, 고르지 않으면 주제 없음(null).
 */
class TopicIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Test
    void topicsAreTheFixedTen() throws Exception {
        mockMvc.perform(get("/api/topics").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].code").value("IT_DEV"))
                .andExpect(jsonPath("$[0].name").value("IT·개발"))
                .andExpect(jsonPath("$[9].name").value("교육·학습"));
    }

    @Test
    void postTopicCanBeChosenChangedAndCleared() throws Exception {
        Member owner = testMembers.create();
        Blog blog = testBlogs.create(owner);
        Cookie[] cookies = testMembers.loginCookies(owner);

        String saved = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), blog, cookies,
                body("\"TRAVEL\"")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(saved, "$.id")).longValue();
        send(get("/api/posts/" + id), blog, null, null).andExpect(jsonPath("$.topic").value("TRAVEL"));
        send(get("/api/manage/posts/" + id), blog, cookies, null).andExpect(jsonPath("$.topic").value("TRAVEL"));
        send(get("/api/posts"), blog, null, null).andExpect(jsonPath("$.content[0].topic").value("TRAVEL"));

        send(put("/api/posts/" + id), blog, cookies, body("null")).andExpect(status().isOk());
        send(get("/api/posts/" + id), blog, null, null).andExpect(jsonPath("$.topic").doesNotExist());

        send(put("/api/posts/" + id), blog, cookies, body("\"SPORTS\"")).andExpect(status().isBadRequest());
    }

    private static String body(String topic) {
        return """
                {"title":"주제 글","contentHtml":"<p>본문</p>","topic":%s,"visibility":"PUBLIC","status":"PUBLISHED"}
                """.formatted(topic);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Blog blog, Cookie[] cookies, String body)
            throws Exception {
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
