package com.nhnacademy.blog.subscription;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 구독·피드·구독자 공개 (T089~T091, SUB-01~03, POST-12, spec US6 독립 테스트:
 * B가 A 구독 → A 새 글이 B 피드에 → A 구독자 수 1 → A의 구독자 공개 글은 B와 주인만, C는 목록에서 빠지고 링크는 안내).
 */
class SubscriptionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    PostRepository postRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member a;
    Member b;
    Member c;
    Blog blogA;

    @BeforeEach
    void setUp() {
        a = testMembers.create();
        b = testMembers.create();
        c = testMembers.create();
        blogA = testBlogs.createPrimary(a);
    }

    @Test
    void subscribeAndUnsubscribeAreIdempotentAndCountFollows() throws Exception {
        subscribe(b, blogA).andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(1));
        // 연달아 눌러도 한 번으로 센다
        subscribe(b, blogA).andExpect(jsonPath("$.subscriberCount").value(1));
        blogInfo(b).andExpect(jsonPath("$.subscriberCount").value(1))
                .andExpect(jsonPath("$.viewer.subscribed").value(true));

        unsubscribe(b, blogA).andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false))
                .andExpect(jsonPath("$.subscriberCount").value(0));
        unsubscribe(b, blogA).andExpect(jsonPath("$.subscriberCount").value(0));
        blogInfo(b).andExpect(jsonPath("$.viewer.subscribed").value(false));
    }

    @Test
    void onlyMembersOtherThanOwnerCanSubscribeVisibleBlogs() throws Exception {
        subscribe(null, blogA).andExpect(status().isUnauthorized());
        subscribe(a, blogA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("blogId"));
        mockMvc.perform(put("/api/blogs/999999/subscription").header(HttpHeaders.HOST, "blog.test")
                        .header("X-Requested-With", "XMLHttpRequest").cookie(testMembers.loginCookies(b)))
                .andExpect(status().isNotFound());
        testBlogs.restrict(blogA);
        subscribe(b, blogA).andExpect(status().isNotFound());
    }

    @Test
    void feedShowsVisiblePostsOfSubscribedBlogsNewestFirstWithCursor() throws Exception {
        Blog blogC = testBlogs.createPrimary(c);
        for (int i = 0; i < 21; i++) {
            post(blogA, "A 글 " + i, Visibility.PUBLIC, 30 - i);
        }
        post(blogA, "A 비공개", Visibility.PRIVATE, 0);
        post(blogC, "구독 안 한 블로그 글", Visibility.PUBLIC, 0);

        mockMvc.perform(get("/api/feed").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isUnauthorized());
        feed(b, null).andExpect(jsonPath("$.content").isEmpty());

        subscribe(b, blogA);
        String body = feed(b, null)
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.content[0].title").value("A 글 20"))
                .andExpect(jsonPath("$.content[0].blog.address").value(blogA.getAddress()))
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");
        feed(b, cursor).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("A 글 0"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void subscribersOnlyPostIsForSubscribersAndOwner() throws Exception {
        Post secret = post(blogA, "구독자만", Visibility.SUBSCRIBERS, 0);
        subscribe(b, blogA);

        for (Member reader : new Member[] {a, b}) {
            detail(secret, testMembers.loginCookies(reader)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("구독자만"));
        }
        detail(secret, testMembers.loginCookies(c)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIBERS_ONLY"))
                .andExpect(jsonPath("$.title").doesNotExist());
        detail(secret, null).andExpect(status().isForbidden());
        // 구독하지 않은 사람의 목록·글 수에서는 빠지고, 구독자의 피드에는 나온다
        mockMvc.perform(get("/api/posts").header(HttpHeaders.HOST, TestBlogs.host(blogA))
                        .cookie(testMembers.loginCookies(c)))
                .andExpect(jsonPath("$.totalElements").value(0));
        blogInfo(c).andExpect(jsonPath("$.postCount").value(0));
        blogInfo(b).andExpect(jsonPath("$.postCount").value(1));
        feed(b, null).andExpect(jsonPath("$.content[0].title").value("구독자만"));
    }

    private Post post(Blog blog, String title, Visibility visibility, int daysAgo) {
        return postRepository.save(Post.published(blog, null, title, new PostBody("<p>본문</p>", "본문", "본문"),
                visibility, null, LocalDateTime.now().minusDays(daysAgo).withNano(0)));
    }

    private ResultActions subscribe(Member member, Blog blog) throws Exception {
        return send(put("/api/blogs/" + blog.getId() + "/subscription"), member);
    }

    private ResultActions unsubscribe(Member member, Blog blog) throws Exception {
        return send(delete("/api/blogs/" + blog.getId() + "/subscription"), member);
    }

    private ResultActions feed(Member member, String cursor) throws Exception {
        var request = get("/api/feed").header(HttpHeaders.HOST, "blog.test").cookie(testMembers.loginCookies(member));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        return mockMvc.perform(request);
    }

    private ResultActions blogInfo(Member member) throws Exception {
        return mockMvc.perform(get("/api/blog").header(HttpHeaders.HOST, TestBlogs.host(blogA))
                .cookie(testMembers.loginCookies(member)));
    }

    private ResultActions detail(Post post, Cookie[] cookies) throws Exception {
        var request = get("/api/posts/" + post.getId()).header(HttpHeaders.HOST, TestBlogs.host(blogA));
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Member member) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blogA)).header("X-Requested-With", "XMLHttpRequest");
        return mockMvc.perform(member == null ? request : request.cookie(testMembers.loginCookies(member)));
    }

}
