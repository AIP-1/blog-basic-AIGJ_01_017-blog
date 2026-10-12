package com.nhnacademy.blog.global.host;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.auth.presentation.CsrfHeaderFilter;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Host → 블로그 해석(T009)과 주인 검사(T050)를 블로그 API(B)로 확인한다.
 */
class CurrentBlogIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Test
    void subdomainResolvesToBlog() throws Exception {
        Blog blog = testBlogs.create(testMembers.create());

        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, blog.getAddress().toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(blog.getAddress()));
    }

    @Test
    void platformOrUnknownAddressIsNotFound() throws Exception {
        mockMvc.perform(get("/api/test/blog"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, "nobody-here"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, "admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedBlogIsNotFoundEvenForOwner() throws Exception {
        Member owner = testMembers.create();
        Blog blog = testBlogs.create(owner);
        testBlogs.delete(blog);

        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, blog.getAddress())
                        .cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void restrictedOrSuspendedOwnerBlogIsHiddenFromOthers() throws Exception {
        Member owner = testMembers.create();
        Blog restricted = testBlogs.create(owner);
        testBlogs.restrict(restricted);

        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, restricted.getAddress()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, restricted.getAddress())
                        .cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isOk());

        Blog suspendedOwners = testBlogs.create(testMembers.create());
        testBlogs.suspendOwner(suspendedOwners);
        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, suspendedOwners.getAddress()))
                .andExpect(status().isNotFound());
    }

    @Test
    void movedBlogApiIsForOwnerOnly() throws Exception {
        Member owner = testMembers.create();
        Blog oldBlog = testBlogs.create(owner);
        testBlogs.move(oldBlog, testBlogs.create(owner));

        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, oldBlog.getAddress()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/test/blog").header(TestBlogs.HEADER, oldBlog.getAddress())
                        .cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerGuardOrder401Then403() throws Exception {
        Member owner = testMembers.create();
        Blog blog = testBlogs.create(owner);

        mockMvc.perform(settings(blog))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(settings(blog).cookie(testMembers.loginCookies(testMembers.create())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(settings(blog).cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void missingBlogIs404BeforeLoginCheck() throws Exception {
        mockMvc.perform(put("/api/test/blog/settings").header(TestBlogs.HEADER, "nobody-here")
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder settings(Blog blog) {
        return put("/api/test/blog/settings").header(TestBlogs.HEADER, blog.getAddress())
                .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE);
    }

}
