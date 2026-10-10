package com.nhnacademy.blog.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
 * 회원 탈퇴 (스텝 19: T108, AUTH-06, spec US10 11번).
 */
class WithdrawalIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "password123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member leaver;
    Member other;
    Blog leaverBlog;
    Blog otherBlog;
    Cookie[] leaverCookies;
    Cookie[] otherCookies;

    @BeforeEach
    void setUp() {
        leaver = testMembers.createWithPassword(PASSWORD);
        other = testMembers.create();
        leaverBlog = testBlogs.createPrimary(leaver);
        otherBlog = testBlogs.createPrimary(other);
        leaverCookies = testMembers.loginCookies(leaver);
        otherCookies = testMembers.loginCookies(other);
    }

    @Test
    void withdrawalRemovesContentReactionsAndLogin() throws Exception {
        Blog second = testBlogs.create(leaver);
        long ownPost = publish(leaverBlog, leaverCookies, "탈퇴할 사람의 글");
        long secondPost = publish(second, leaverCookies, "두 번째 블로그 글");
        long otherPost = publish(otherBlog, otherCookies, "남의 글");
        long parent = comment(otherBlog, otherPost, leaverCookies, "탈퇴할 사람의 댓글");
        comment(otherBlog, otherPost, otherCookies, "답글", parent);
        comment(otherBlog, otherPost, leaverCookies, "답글 없는 댓글", null);
        send(put("/api/posts/" + otherPost + "/like"), otherBlog, leaverCookies, null).andExpect(status().isOk());
        send(put("/api/blogs/" + otherBlog.getId() + "/subscription"), otherBlog, leaverCookies, null)
                .andExpect(status().isOk());
        send(get("/api/posts/" + otherPost), otherBlog, null, null)
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(3));

        // 본인 확인
        send(delete("/api/me"), null, leaverCookies, "{\"password\":\"wrong-password1\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        send(delete("/api/me"), null, null, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isUnauthorized());

        send(delete("/api/me"), null, leaverCookies, "{\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0));

        // 로그인할 수 없고, 남은 쿠키는 비회원 취급
        mockMvc.perform(post("/api/auth/login").header(HttpHeaders.HOST, "blog.test")
                        .header("X-Requested-With", "XMLHttpRequest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + leaver.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        send(get("/api/me"), null, leaverCookies, null).andExpect(status().isUnauthorized());

        // 블로그·글은 모두 없음, 주소는 다시 쓸 수 없음
        send(get("/api/blog"), leaverBlog, otherCookies, null).andExpect(status().isNotFound());
        send(get("/api/blog"), second, otherCookies, null).andExpect(status().isNotFound());
        send(get("/api/posts/" + ownPost), leaverBlog, null, null).andExpect(status().isNotFound());
        send(get("/api/posts/" + secondPost), second, null, null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/blogs/address-availability").param("address", leaverBlog.getAddress())
                        .header(HttpHeaders.HOST, "blog.test").cookie(otherCookies))
                .andExpect(jsonPath("$.available").value(false));

        // 공감·구독은 사라져 수치가 맞고, 댓글은 지워지고 답글이 있는 댓글만 자리로 남는다
        send(get("/api/posts/" + otherPost), otherBlog, null, null)
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(1));
        send(get("/api/blog"), otherBlog, null, null).andExpect(jsonPath("$.subscriberCount").value(0));
        send(get("/api/posts/" + otherPost + "/comments"), otherBlog, null, null)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].state").value("DELETED"))
                .andExpect(jsonPath("$.content[0].replies[0].content").value("답글"));

        assertThat(jdbcTemplate.queryForMap("SELECT status, email, password_hash, withdrawn_at FROM member WHERE id = ?",
                leaver.getId()))
                .containsEntry("status", "WITHDRAWN")
                .containsEntry("email", null)
                .containsEntry("password_hash", null);
    }

    @Test
    void sameEmailCanSignUpAgainAfterWithdrawal() throws Exception {
        send(delete("/api/me"), null, leaverCookies, "{\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member WHERE email = ?", Integer.class,
                leaver.getEmail())).isZero();
    }

    private long publish(Blog blog, Cookie[] cookies, String title) throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), blog, cookies,
                "{\"title\":\"" + title + "\",\"contentHtml\":\"<p>본문</p>\",\"visibility\":\"PUBLIC\",\"status\":\"PUBLISHED\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long comment(Blog blog, long post, Cookie[] cookies, String content) throws Exception {
        return comment(blog, post, cookies, content, null);
    }

    private long comment(Blog blog, long post, Cookie[] cookies, String content, Long parentId) throws Exception {
        String response = send(post("/api/posts/" + post + "/comments")
                        .header("Idempotency-Key", UUID.randomUUID().toString()), blog, cookies,
                "{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Blog blog, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, blog == null ? "blog.test" : TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
