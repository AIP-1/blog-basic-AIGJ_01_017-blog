package com.nhnacademy.blog.blog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 블로그 정보 조회·수정 (T023, BLOG-02, BLOG-03).
 */
class BlogInfoIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    Member owner;
    Member other;
    Blog blog;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        other = testMembers.create();
        blog = testBlogs.create(owner);
    }

    @Test
    void blogInfoCountsOnlyPostsTheViewerCanSee() throws Exception {
        testPosts.published(blog, Visibility.PUBLIC);
        testPosts.published(blog, Visibility.PRIVATE);
        testPosts.draft(blog);

        getBlog(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(blog.getAddress()))
                .andExpect(jsonPath("$.owner.id").value(owner.getId()))
                .andExpect(jsonPath("$.owner.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.subscriberCount").value(0))
                .andExpect(jsonPath("$.listLayout").value("LIST"))
                .andExpect(jsonPath("$.accentColor").value("BLUE"))
                .andExpect(jsonPath("$.viewer.isOwner").value(false))
                .andExpect(jsonPath("$.viewer.subscribed").value(false))
                .andExpect(jsonPath("$.restriction").doesNotExist());
        // 주인에게는 비공개 발행 글도 세지만, 임시저장은 블로그 화면의 글이 아니다
        getBlog(testMembers.loginCookies(owner))
                .andExpect(jsonPath("$.postCount").value(2))
                .andExpect(jsonPath("$.viewer.isOwner").value(true));
    }

    @Test
    void ownerUpdatesNameAndDescription() throws Exception {
        update(testMembers.loginCookies(owner), """
                {"name":"  새 이름 ","description":"새 소개","address":"changed-address"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새 이름"))
                .andExpect(jsonPath("$.description").value("새 소개"))
                // 주소는 바꿀 수 없다
                .andExpect(jsonPath("$.address").value(blog.getAddress()));

        // 보낸 항목만 바뀌고, 빈 소개는 소개를 지운다
        update(testMembers.loginCookies(owner), "{\"description\":\"\"}")
                .andExpect(jsonPath("$.name").value("새 이름"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void onlyOwnerCanUpdateAndPermissionComesBeforeInputErrors() throws Exception {
        String invalid = "{\"name\":\"" + "가".repeat(51) + "\"}";

        update(null, invalid).andExpect(status().isUnauthorized());
        update(testMembers.loginCookies(other), invalid)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        update(testMembers.loginCookies(owner), invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        update(testMembers.loginCookies(owner), "{\"name\":\"   \"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrDeletedBlogIs404() throws Exception {
        mockMvc.perform(get("/api/blog").header(HttpHeaders.HOST, "no-such-blog.blog.test"))
                .andExpect(status().isNotFound());
        testBlogs.delete(blog);
        getBlog(testMembers.loginCookies(owner)).andExpect(status().isNotFound());
    }

    private ResultActions getBlog(Cookie[] cookies) throws Exception {
        var request = get("/api/blog").header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

    private ResultActions update(Cookie[] cookies, String body) throws Exception {
        var request = patch("/api/blog")
                .header(HttpHeaders.HOST, TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
