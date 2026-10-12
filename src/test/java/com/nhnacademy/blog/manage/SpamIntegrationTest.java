package com.nhnacademy.blog.manage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 차단·금칙어 (스텝 20: T084, MNG-04, spec US13 2~3). */
class SpamIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member spammer;
    Blog blog;
    Cookie[] ownerCookies;
    Cookie[] spammerCookies;
    long post;

    @BeforeEach
    void setUp() throws Exception {
        owner = testMembers.create();
        spammer = testMembers.create();
        blog = testBlogs.createPrimary(owner);
        ownerCookies = testMembers.loginCookies(owner);
        spammerCookies = testMembers.loginCookies(spammer);
        post = publish(blog, ownerCookies, "글");
    }

    @Test
    void blockedMemberCannotCommentWriteGuestbookOrSubscribe() throws Exception {
        String host = TestBlogs.host(blog);
        send(put("/api/blogs/" + blog.getId() + "/subscription"), host, spammerCookies, null).andExpect(status().isOk());

        send(post("/api/manage/blocked-members"), host, spammerCookies, "{\"memberId\":" + owner.getId() + "}")
                .andExpect(status().isForbidden());
        send(post("/api/manage/blocked-members"), host, ownerCookies, "{\"memberId\":" + owner.getId() + "}")
                .andExpect(status().isBadRequest());
        send(post("/api/manage/blocked-members"), host, ownerCookies,
                "{\"memberId\":" + spammer.getId() + ",\"memo\":\"광고 댓글 반복\"}").andExpect(status().isCreated());
        send(post("/api/manage/blocked-members"), host, ownerCookies, "{\"memberId\":" + spammer.getId() + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BLOCKED"));

        // 기존 구독은 차단할 때 지워졌다
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription WHERE member_id = ? AND blog_id = ?",
                Integer.class, spammer.getId(), blog.getId())).isZero();
        send(post("/api/posts/" + post + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()), host,
                spammerCookies, "{\"content\":\"광고\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOCKED_BY_BLOG"));
        send(post("/api/guestbook").header("Idempotency-Key", UUID.randomUUID().toString()), host, spammerCookies,
                "{\"content\":\"광고\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOCKED_BY_BLOG"));
        send(put("/api/blogs/" + blog.getId() + "/subscription"), host, spammerCookies, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOCKED_BY_BLOG"));

        send(get("/api/manage/blocked-members"), host, ownerCookies, null)
                .andExpect(jsonPath("$.content[0].member.id").value(spammer.getId()))
                .andExpect(jsonPath("$.content[0].memo").value("광고 댓글 반복"));
        send(delete("/api/manage/blocked-members/" + spammer.getId()), host, ownerCookies, null)
                .andExpect(status().isNoContent());
        send(post("/api/posts/" + post + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()), host,
                spammerCookies, "{\"content\":\"이제 됨\"}").andExpect(status().isCreated());
    }

    @Test
    void bannedWordsRejectNewCommentsIgnoringCase() throws Exception {
        String host = TestBlogs.host(blog);
        String created = send(post("/api/manage/banned-words"), host, ownerCookies, "{\"word\":\"Casino\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        send(post("/api/manage/banned-words"), host, ownerCookies, "{\"word\":\"casino\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_TAKEN"));
        send(post("/api/manage/banned-words"), host, ownerCookies, "{\"word\":\" \"}")
                .andExpect(status().isBadRequest());

        send(post("/api/posts/" + post + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()), host,
                spammerCookies, "{\"content\":\"최고의 CASINO 사이트\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNED_WORD"));
        send(post("/api/guestbook").header("Idempotency-Key", UUID.randomUUID().toString()), host, spammerCookies,
                "{\"content\":\"casino\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNED_WORD"));
        send(get("/api/manage/banned-words"), host, ownerCookies, null).andExpect(jsonPath("$[*].word", contains("Casino")));

        send(delete("/api/manage/banned-words/" + id), host, ownerCookies, null).andExpect(status().isNoContent());
        send(post("/api/posts/" + post + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()), host,
                spammerCookies, "{\"content\":\"casino\"}").andExpect(status().isCreated());
    }

    @Test
    void editsAreCheckedLikeNewWrites() throws Exception {
        String host = TestBlogs.host(blog);
        long comment = idOf(send(post("/api/posts/" + post + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()),
                host, spammerCookies, "{\"content\":\"깨끗한 댓글\"}").andExpect(status().isCreated()));
        long guestbook = idOf(send(post("/api/guestbook").header("Idempotency-Key", UUID.randomUUID().toString()), host,
                spammerCookies, "{\"content\":\"깨끗한 방명록\"}").andExpect(status().isCreated()));

        // 깨끗하게 쓴 뒤 고쳐서 금칙어를 넣을 수 없다
        send(post("/api/manage/banned-words"), host, ownerCookies, "{\"word\":\"카지노\"}").andExpect(status().isCreated());
        send(patch("/api/comments/" + comment), host, spammerCookies, "{\"content\":\"카지노 홍보\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNED_WORD"));
        send(patch("/api/guestbook/" + guestbook), host, spammerCookies, "{\"content\":\"카지노 홍보\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNED_WORD"));
        send(patch("/api/comments/" + comment), host, spammerCookies, "{\"content\":\"고친 댓글\"}")
                .andExpect(status().isOk());

        // 차단되면 전에 쓴 댓글·방명록도 고칠 수 없다(지우기는 된다)
        send(post("/api/manage/blocked-members"), host, ownerCookies, "{\"memberId\":" + spammer.getId() + "}")
                .andExpect(status().isCreated());
        send(patch("/api/comments/" + comment), host, spammerCookies, "{\"content\":\"다시 고침\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOCKED_BY_BLOG"));
        send(patch("/api/guestbook/" + guestbook), host, spammerCookies, "{\"content\":\"다시 고침\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOCKED_BY_BLOG"));
        send(delete("/api/comments/" + comment), host, spammerCookies, null).andExpect(status().isNoContent());
    }

    private long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void atMostOneHundredBannedWords() throws Exception {
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.update("INSERT INTO blog_banned_word (blog_id, word) VALUES (?, ?)", blog.getId(), "w" + i);
        }
        send(post("/api/manage/banned-words"), TestBlogs.host(blog), ownerCookies, "{\"word\":\"one-more\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BANNED_WORD_LIMIT"));
    }

    private long publish(Blog target, Cookie[] cookies, String title) throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                TestBlogs.host(target), cookies,
                "{\"title\":\"" + title + "\",\"contentHtml\":\"<p>본문</p>\",\"visibility\":\"PUBLIC\",\"status\":\"PUBLISHED\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String host, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, host).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
