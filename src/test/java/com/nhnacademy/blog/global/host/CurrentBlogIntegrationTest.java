package com.nhnacademy.blog.global.host;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.CsrfHeaderFilter;
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

        mockMvc.perform(get("/api/test/blog").header("Host", TestBlogs.host(blog) + ":8080"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(blog.getAddress()));
    }

    @Test
    void platformOrUnknownAddressIsNotFound() throws Exception {
        mockMvc.perform(get("/api/test/blog").header("Host", "blog.test"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/test/blog").header("Host", "nobody-here.blog.test"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/test/blog").header("Host", "admin.blog.test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedBlogIsNotFoundEvenForOwner() throws Exception {
        Member owner = testMembers.create();
        Blog blog = testBlogs.create(owner);
        testBlogs.delete(blog);

        mockMvc.perform(get("/api/test/blog").header("Host", TestBlogs.host(blog))
                        .cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void restrictedOrSuspendedOwnerBlogIsHiddenFromOthers() throws Exception {
        Member owner = testMembers.create();
        Blog restricted = testBlogs.create(owner);
        testBlogs.restrict(restricted);

        mockMvc.perform(get("/api/test/blog").header("Host", TestBlogs.host(restricted)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/test/blog").header("Host", TestBlogs.host(restricted))
                        .cookie(testMembers.loginCookies(owner)))
                .andExpect(status().isOk());

        Blog suspendedOwners = testBlogs.create(testMembers.create());
        testBlogs.suspendOwner(suspendedOwners);
        mockMvc.perform(get("/api/test/blog").header("Host", TestBlogs.host(suspendedOwners)))
                .andExpect(status().isNotFound());
    }

    @Test
    void movedBlogApiIsForOwnerOnly() throws Exception {
        Member owner = testMembers.create();
        Blog oldBlog = testBlogs.create(owner);
        testBlogs.move(oldBlog, testBlogs.create(owner));

        mockMvc.perform(get("/api/test/blog").header("Host", TestBlogs.host(oldBlog)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/test/blog").header("Host", TestBlogs.host(oldBlog))
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
        mockMvc.perform(put("/api/test/blog/settings").header("Host", "nobody-here.blog.test")
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder settings(Blog blog) {
        return put("/api/test/blog/settings").header("Host", TestBlogs.host(blog))
                .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE);
    }

}
