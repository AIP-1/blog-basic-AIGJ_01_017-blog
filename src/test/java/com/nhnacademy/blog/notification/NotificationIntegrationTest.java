package com.nhnacademy.blog.notification;

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
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
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
 * 알림 (T113, SUB-04, spec US12: 내 글에 댓글·답글·공감, 새 구독자가 생기면 알림, 읽음 처리).
 */
class NotificationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member reader;
    Blog blog;
    Post post;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        reader = testMembers.create();
        blog = testBlogs.createPrimary(owner);
        post = testPosts.published(blog, Visibility.PUBLIC);
    }

    @Test
    void commentLikeAndSubscribeNotifyOwnerButNotYourself() throws Exception {
        long commentId = comment(reader, "{\"content\":\"잘 봤어요\"}");
        like(reader);
        like(reader);   // 연타해도 알림은 하나
        send(put("/api/blogs/" + blog.getId() + "/subscription"), reader, null).andExpect(status().isOk());
        comment(owner, "{\"content\":\"내 글에 내 댓글\"}");   // 나에게 알리지 않음
        like(owner);

        list(owner)
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].type").value("SUBSCRIBE"))
                .andExpect(jsonPath("$.content[0].message").value(reader.getNickname() + "님이 " + blog.getName()
                        + "을(를) 구독했습니다."))
                .andExpect(jsonPath("$.content[0].link").value("http://" + TestBlogs.host(blog) + "/"))
                .andExpect(jsonPath("$.content[1].type").value("LIKE"))
                .andExpect(jsonPath("$.content[1].link").value("http://" + TestBlogs.host(blog) + "/" + post.getId()))
                .andExpect(jsonPath("$.content[2].type").value("COMMENT"))
                .andExpect(jsonPath("$.content[2].message").value(reader.getNickname() + "님이 \"" + post.getTitle()
                        + "\"에 댓글을 남겼습니다."))
                .andExpect(jsonPath("$.content[2].link").value(
                        "http://" + TestBlogs.host(blog) + "/" + post.getId() + "#comment-" + commentId))
                .andExpect(jsonPath("$.content[2].read").value(false));
        list(reader).andExpect(jsonPath("$.content").isEmpty());
        me(owner).andExpect(jsonPath("$.unreadNotificationCount").value(3));
    }

    @Test
    void replyNotifiesParentAuthorAndOwnerOnce() throws Exception {
        Member third = testMembers.create();
        long parent = comment(reader, "{\"content\":\"질문\"}");
        comment(third, "{\"content\":\"답\",\"parentId\":" + parent + "}");
        long ownerParent = comment(owner, "{\"content\":\"주인 댓글\"}");
        comment(reader, "{\"content\":\"주인에게 답\",\"parentId\":" + ownerParent + "}");

        list(reader).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("REPLY"));
        // 주인: reader 댓글, third 답글(COMMENT), reader의 주인 댓글 답글(REPLY 하나만)
        list(owner).andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].type").value("REPLY"))
                .andExpect(jsonPath("$.content[1].type").value("COMMENT"))
                .andExpect(jsonPath("$.content[2].type").value("COMMENT"));
    }

    @Test
    void notificationsOfGoneTargetsDropOutAndReadingWorks() throws Exception {
        comment(reader, "{\"content\":\"지워질 댓글\"}");
        like(reader);
        testPosts.delete(post);
        list(owner).andExpect(jsonPath("$.content").isEmpty());

        Post other = testPosts.published(blog, Visibility.PUBLIC);
        mockMvc.perform(put("/api/posts/" + other.getId() + "/like")
                        .header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest")
                        .cookie(testMembers.loginCookies(reader)))
                .andExpect(status().isOk());
        String listed = list(owner).andExpect(jsonPath("$.content", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(listed, "$.content[0].id")).longValue();

        // 남의 알림은 없는 것과 같다
        send(put("/api/me/notifications/" + id + "/read"), reader, null).andExpect(status().isNotFound());
        send(put("/api/me/notifications/" + id + "/read"), owner, null).andExpect(status().isNoContent());
        list(owner).andExpect(jsonPath("$.content[0].read").value(true));
        unread(owner).andExpect(jsonPath("$.count").value(2));   // 지운 글의 알림은 읽기 전까지 센다
        send(put("/api/me/notifications/read-all"), owner, null).andExpect(status().isNoContent());
        unread(owner).andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isUnauthorized());
    }

    private long comment(Member member, String body) throws Exception {
        String response = send(post("/api/posts/" + post.getId() + "/comments")
                .header("Idempotency-Key", UUID.randomUUID().toString()), member, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void like(Member member) throws Exception {
        send(put("/api/posts/" + post.getId() + "/like"), member, null).andExpect(status().isOk());
    }

    private ResultActions list(Member member) throws Exception {
        return mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.HOST, "blog.test")
                .cookie(testMembers.loginCookies(member)));
    }

    private ResultActions unread(Member member) throws Exception {
        return mockMvc.perform(get("/api/me/notifications/unread-count").header(HttpHeaders.HOST, "blog.test")
                .cookie(testMembers.loginCookies(member)));
    }

    private ResultActions me(Member member) throws Exception {
        return mockMvc.perform(get("/api/me").header(HttpHeaders.HOST, "blog.test")
                .cookie(testMembers.loginCookies(member)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Member member, String body) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest")
                .cookie(testMembers.loginCookies(member));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

}
