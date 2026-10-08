package com.nhnacademy.blog.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * 답글 (T069, CMT-05). 한 단계, 답글 있는 댓글을 지우면 "삭제된 댓글입니다" 자리로 남는다.
 */
class ReplyIntegrationTest extends IntegrationTestSupport {

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

    Blog blog;
    Post post;
    Member reader;
    Cookie[] readerCookies;
    Cookie[] ownerCookies;

    @BeforeEach
    void setUp() {
        Member owner = testMembers.create();
        reader = testMembers.create();
        blog = testBlogs.create(owner);
        post = testPosts.published(blog, Visibility.PUBLIC);
        readerCookies = testMembers.loginCookies(reader);
        ownerCookies = testMembers.loginCookies(owner);
    }

    @Test
    void repliesAreNestedUnderParentInWrittenOrder() throws Exception {
        long parent = write(readerCookies, "질문 있어요", null);
        long other = write(readerCookies, "다른 댓글", null);
        long firstReply = write(ownerCookies, "답변 1", parent);
        long secondReply = write(readerCookies, "답변 2", parent);

        list().andExpect(jsonPath("$.content[*].id", contains((int) parent, (int) other)))
                .andExpect(jsonPath("$.content[0].replies[*].id", contains((int) firstReply, (int) secondReply)))
                .andExpect(jsonPath("$.content[0].replies[0].parentId").value(parent))
                .andExpect(jsonPath("$.content[0].replies[0].replies", hasSize(0)))
                .andExpect(jsonPath("$.content[1].replies", hasSize(0)))
                .andExpect(jsonPath("$.totalCount").value(4));
        assertThat(jdbcTemplate.queryForObject("SELECT comment_count FROM post WHERE id = ?", Integer.class,
                post.getId())).isEqualTo(4);
    }

    @Test
    void replyOfReplyAndForeignParentAreRejected() throws Exception {
        long parent = write(readerCookies, "부모", null);
        long reply = write(ownerCookies, "답글", parent);
        Post otherPost = testPosts.published(blog, Visibility.PUBLIC);

        send(readerCookies, "답글의 답글", reply)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentId"));
        mockMvc.perform(post("/api/posts/" + otherPost.getId() + "/comments")
                        .header(HttpHeaders.HOST, TestBlogs.host(blog))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"다른 글 댓글에 답글\",\"parentId\":" + parent + "}")
                        .cookie(readerCookies))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletedParentWithRepliesStaysAsPlaceholder() throws Exception {
        long parent = write(readerCookies, "지울 부모", null);
        long reply = write(ownerCookies, "남을 답글", parent);
        long lonely = write(readerCookies, "답글 없는 댓글", null);

        remove(readerCookies, parent).andExpect(status().isNoContent());
        remove(readerCookies, lonely).andExpect(status().isNoContent());

        list().andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(parent))
                .andExpect(jsonPath("$.content[0].state").value("DELETED"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.content[0].author").doesNotExist())
                .andExpect(jsonPath("$.content[0].viewer.canDelete").value(false))
                .andExpect(jsonPath("$.content[0].replies[0].id").value(reply))
                .andExpect(jsonPath("$.totalCount").value(1));
        // 지운 댓글에는 답글을 달 수 없다
        send(readerCookies, "늦은 답글", parent).andExpect(status().isBadRequest());

        // 마지막 답글까지 지우면 자리도 사라진다
        remove(ownerCookies, reply).andExpect(status().isNoContent());
        list().andExpect(jsonPath("$.content", hasSize(0))).andExpect(jsonPath("$.totalCount").value(0));
    }

    private long write(Cookie[] cookies, String content, Long parentId) throws Exception {
        String response = send(cookies, content, parentId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions send(Cookie[] cookies, String content, Long parentId) throws Exception {
        String body = parentId == null
                ? "{\"content\":\"" + content + "\"}"
                : "{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}";
        return mockMvc.perform(post("/api/posts/" + post.getId() + "/comments")
                .header(HttpHeaders.HOST, TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(cookies));
    }

    private ResultActions list() throws Exception {
        return mockMvc.perform(get("/api/posts/" + post.getId() + "/comments")
                .header(HttpHeaders.HOST, TestBlogs.host(blog)));
    }

    private ResultActions remove(Cookie[] cookies, long commentId) throws Exception {
        return mockMvc.perform(delete("/api/comments/" + commentId)
                .header(HttpHeaders.HOST, TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(cookies));
    }

}
