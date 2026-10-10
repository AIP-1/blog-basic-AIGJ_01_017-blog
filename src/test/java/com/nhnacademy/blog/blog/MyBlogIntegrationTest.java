package com.nhnacademy.blog.blog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 마이페이지 내 블로그 (BLOG-08): 내 활성 블로그 목록과 대표 블로그 바꾸기. 두 번째 블로그부터의 입구다(BLOG-01).
 */
class MyBlogIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestPosts testPosts;

    @Autowired
    BlogRepository blogRepository;

    Cookie[] cookies;

    @BeforeEach
    void setUp() {
        cookies = testMembers.loginCookies(testMembers.create());
    }

    @Test
    void anonymousGets401() throws Exception {
        send(get("/api/me/blogs"), null, null).andExpect(status().isUnauthorized());
        send(put("/api/me/primary-blog"), null, "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void listsMyActiveBlogsInCreatedOrderWithPublishedPostCount() throws Exception {
        send(get("/api/me/blogs"), cookies, null).andExpect(jsonPath("$.length()").value(0));
        long first = createBlog("첫 블로그");
        long second = createBlog("둘째 블로그");
        Blog firstBlog = blogRepository.findById(first).orElseThrow();
        testPosts.published(firstBlog, Visibility.PUBLIC);
        testPosts.published(firstBlog, Visibility.PRIVATE);
        testPosts.draft(firstBlog);
        // 남의 블로그는 나오지 않는다
        createBlogAs(testMembers.loginCookies(testMembers.create()), "남의 블로그");

        send(get("/api/me/blogs"), cookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[0].postCount").value(2))
                .andExpect(jsonPath("$[0].movedTo").isEmpty())
                .andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[1].name").value("둘째 블로그"))
                .andExpect(jsonPath("$[1].isPrimary").value(false))
                .andExpect(jsonPath("$[1].postCount").value(0));
    }

    @Test
    void primaryBlogChangesToAnotherOfMine() throws Exception {
        long first = createBlog("첫 블로그");
        long second = createBlog("둘째 블로그");
        String secondAddress = blogRepository.findById(second).orElseThrow().getAddress();

        send(put("/api/me/primary-blog"), cookies, "{\"blogId\":" + second + "}").andExpect(status().isNoContent());

        send(get("/api/me/blogs"), cookies, null)
                .andExpect(jsonPath("$[0].isPrimary").value(false))
                .andExpect(jsonPath("$[1].isPrimary").value(true));
        // 글쓰기 버튼(AUTH-04)과 머리글의 내 블로그가 보는 대표 블로그도 바뀐다
        send(get("/api/me"), cookies, null).andExpect(jsonPath("$.primaryBlog.address").value(secondAddress));
        // 이미 대표인 블로그를 다시 골라도 된다
        send(put("/api/me/primary-blog"), cookies, "{\"blogId\":" + second + "}").andExpect(status().isNoContent());
        send(put("/api/me/primary-blog"), cookies, "{\"blogId\":" + first + "}").andExpect(status().isNoContent());
        send(get("/api/me/blogs"), cookies, null).andExpect(jsonPath("$[0].isPrimary").value(true));
    }

    @Test
    void onlyMyActiveBlogCanBePrimary() throws Exception {
        createBlog("첫 블로그");
        long others = createBlogAs(testMembers.loginCookies(testMembers.create()), "남의 블로그");

        send(put("/api/me/primary-blog"), cookies, "{\"blogId\":" + others + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("blogId"));
        send(put("/api/me/primary-blog"), cookies, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("blogId"));
    }

    private long createBlog(String name) throws Exception {
        return createBlogAs(cookies, name);
    }

    private long createBlogAs(Cookie[] owner, String name) throws Exception {
        String address = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String body = send(post("/api/blogs"), owner, "{\"address\":\"%s\",\"name\":\"%s\"}".formatted(address, name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie[] cookies, String body)
            throws Exception {
        request.header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (cookies != null) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

}
