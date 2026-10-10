package com.nhnacademy.blog.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * quickstart "권한·가시성" 표를 직접 요청으로 확인한다 (T052, COM-01, COM-02, ADMIN-01, spec US4).
 * 화면을 거치지 않고 API·화면 주소를 바로 불러도 서버가 같은 결과를 낸다(헌법 원칙 IV).
 */
class AccessControlIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    Member a;
    Member b;
    Blog alpha;
    Blog beta;
    Cookie[] bCookies;

    @BeforeEach
    void setUp() {
        a = testMembers.create();
        b = testMembers.create();
        alpha = testBlogs.create(a);
        beta = testBlogs.create(b);
        bCookies = testMembers.loginCookies(b);
    }

    @Test
    void memberBCannotEditOrDeleteAsPostsOrManageAsBlog() throws Exception {
        Post post = testPosts.published(alpha, Visibility.PUBLIC);

        mockMvc.perform(put("/api/posts/" + post.getId())
                        .header(HttpHeaders.HOST, TestBlogs.host(alpha))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"contentHtml\":\"\",\"visibility\":\"PUBLIC\",\"status\":\"PUBLISHED\"}")
                        .cookie(bCookies))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/posts/" + post.getId())
                        .header(HttpHeaders.HOST, TestBlogs.host(alpha))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(bCookies))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/manage/posts/" + post.getId())
                        .header(HttpHeaders.HOST, TestBlogs.host(alpha)).cookie(bCookies))
                .andExpect(status().isForbidden());
    }

    @Test
    void privatePostIs404ForOthersOnApiAndScreen() throws Exception {
        Post privatePost = testPosts.published(alpha, Visibility.PRIVATE);

        for (Cookie[] cookies : new Cookie[][] {null, bCookies}) {
            var api = get("/api/posts/" + privatePost.getId()).header(HttpHeaders.HOST, TestBlogs.host(alpha));
            mockMvc.perform(cookies == null ? api : api.cookie(cookies))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            var screen = get("/" + privatePost.getId()).header(HttpHeaders.HOST, TestBlogs.host(alpha));
            mockMvc.perform(cookies == null ? screen : screen.cookie(cookies))
                    .andExpect(status().isNotFound());
        }
        // 목록·글 수에서도 빠진다
        mockMvc.perform(get("/api/blog").header(HttpHeaders.HOST, TestBlogs.host(alpha)).cookie(bCookies))
                .andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    void postNumberOnAnotherBlogAddressRedirectsOnlyWhenVisible() throws Exception {
        Post publicPost = testPosts.published(alpha, Visibility.PUBLIC);
        Post privatePost = testPosts.published(alpha, Visibility.PRIVATE);

        // 화면 주소: 볼 수 있으면 지금 소속 블로그로 301, 아니면 404
        mockMvc.perform(get("/" + publicPost.getId()).header(HttpHeaders.HOST, TestBlogs.host(beta)))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://" + TestBlogs.host(alpha) + "/" + publicPost.getId()));
        mockMvc.perform(get("/" + privatePost.getId()).header(HttpHeaders.HOST, TestBlogs.host(beta)))
                .andExpect(status().isNotFound());
        // API는 301 없이 404
        mockMvc.perform(get("/api/posts/" + publicPost.getId()).header(HttpHeaders.HOST, TestBlogs.host(beta)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminAreaIsForAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard").cookie(bCookies))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 관리자는 통과한다(대시보드는 스텝 18)
        mockMvc.perform(get("/api/admin/dashboard").cookie(testMembers.loginCookies(testMembers.admin())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me").cookie(bCookies)).andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void unexpectedErrorShowsNoInternals() throws Exception {
        mockMvc.perform(get("/api/test/errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret-table"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

}
