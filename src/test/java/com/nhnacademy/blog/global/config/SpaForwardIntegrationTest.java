package com.nhnacademy.blog.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostBody;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 화면 주소 처리 (T010): 블로그·글을 확인한 뒤 index.html, 404 화면, 301.
 */
class SpaForwardIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    PostRepository postRepository;

    @Test
    void platformPagesServeTheApp() throws Exception {
        mockMvc.perform(page("blog.test", "/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(page("blog.test", "/signup"))
                .andExpect(status().isOk());
        mockMvc.perform(page("localhost", "/login"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownBlogAddressIs404Page() throws Exception {
        mockMvc.perform(page("nobody-here.blog.test", "/"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(page("admin.blog.test", "/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blogPagesServeTheApp() throws Exception {
        Blog blog = testBlogs.create(testMembers.create());

        mockMvc.perform(page(TestBlogs.host(blog), "/"))
                .andExpect(status().isOk());
        mockMvc.perform(page(TestBlogs.host(blog), "/category/12"))
                .andExpect(status().isOk());
    }

    @Test
    void deletedBlogIs404Page() throws Exception {
        Blog blog = testBlogs.create(testMembers.create());
        testBlogs.delete(blog);

        mockMvc.perform(page(TestBlogs.host(blog), "/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void movedBlogRedirectsWithSamePathExceptOwnersManagePages() throws Exception {
        Member owner = testMembers.create();
        Blog oldBlog = testBlogs.create(owner);
        Blog newBlog = testBlogs.create(owner);
        testBlogs.move(oldBlog, newBlog);

        mockMvc.perform(page(TestBlogs.host(oldBlog), "/category/3").queryParam("page", "2"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "http://" + TestBlogs.host(newBlog) + "/category/3?page=2"));
        mockMvc.perform(page(TestBlogs.host(oldBlog), "/manage/settings"))
                .andExpect(status().isMovedPermanently());
        mockMvc.perform(page(TestBlogs.host(oldBlog), "/manage/settings").cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void movedToDeletedBlogIs404() throws Exception {
        Member owner = testMembers.create();
        Blog oldBlog = testBlogs.create(owner);
        Blog newBlog = testBlogs.create(owner);
        testBlogs.move(oldBlog, newBlog);
        testBlogs.delete(newBlog);

        mockMvc.perform(page(TestBlogs.host(oldBlog), "/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postOfAnotherBlogRedirectsWhenVisible() throws Exception {
        Blog alpha = testBlogs.create(testMembers.create());
        Blog beta = testBlogs.create(testMembers.create());
        Post publicPost = post(alpha, Visibility.PUBLIC);
        Post privatePost = post(alpha, Visibility.PRIVATE);

        mockMvc.perform(page(TestBlogs.host(alpha), "/" + publicPost.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(page(TestBlogs.host(beta), "/" + publicPost.getId()))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "http://" + TestBlogs.host(alpha) + "/" + publicPost.getId()));
        mockMvc.perform(page(TestBlogs.host(beta), "/" + privatePost.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(page(TestBlogs.host(beta), "/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invisiblePostIs404PageEvenWhenLoggedIn() throws Exception {
        Member owner = testMembers.create();
        Blog blog = testBlogs.create(owner);
        Post privatePost = post(blog, Visibility.PRIVATE);

        mockMvc.perform(page(TestBlogs.host(blog), "/" + privatePost.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(page(TestBlogs.host(blog), "/" + privatePost.getId())
                        .cookie(testMembers.loginCookies(testMembers.create())))
                .andExpect(status().isNotFound());
        mockMvc.perform(page(TestBlogs.host(blog), "/" + privatePost.getId()).cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void postLeftInMovedBlogStaysAtOldAddress() throws Exception {
        Member owner = testMembers.create();
        Blog oldBlog = testBlogs.create(owner);
        Post leftBehind = post(oldBlog, Visibility.PUBLIC);
        testBlogs.move(oldBlog, testBlogs.create(owner));

        mockMvc.perform(page(TestBlogs.host(oldBlog), "/" + leftBehind.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void subscribersOnlyPostServesAppForNotice() throws Exception {
        Blog blog = testBlogs.create(testMembers.create());
        Post post = post(blog, Visibility.SUBSCRIBERS);

        mockMvc.perform(page(TestBlogs.host(blog), "/" + post.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void assetsAndApiAreNotPages() throws Exception {
        mockMvc.perform(page("blog.test", "/assets/missing.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(page("blog.test", "/api/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder page(String host, String path) {
        return get(path).header("Host", host).accept(MediaType.TEXT_HTML);
    }

    private Post post(Blog blog, Visibility visibility) {
        return postRepository.save(Post.published(blog, null, "제목", new PostBody("<p>본문</p>", "본문", "본문"), visibility, null,
                LocalDateTime.now()));
    }

}
