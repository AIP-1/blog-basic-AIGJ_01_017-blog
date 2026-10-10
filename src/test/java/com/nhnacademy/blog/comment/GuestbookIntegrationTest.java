package com.nhnacademy.blog.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.Guestbook;
import com.nhnacademy.blog.comment.domain.GuestbookRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
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
 * 방명록 (T066, CMT-04, spec US7 시나리오 3: 최신순 20개씩 페이지, 댓글과 같은 규칙(비밀·답글)).
 */
class GuestbookIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    GuestbookRepository guestbookRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member visitor;
    Member stranger;
    Blog blog;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        visitor = testMembers.create();
        stranger = testMembers.create();
        blog = testBlogs.create(owner);
    }

    @Test
    void memberWritesAndEveryoneSeesNewestFirstInPagesOf20() throws Exception {
        for (int i = 0; i < 21; i++) {
            guestbookRepository.save(Guestbook.write(blog, visitor, "방명록 " + i, false));
        }
        write(stranger, "{\"content\":\"  반가워요 \"}", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("반가워요"))
                .andExpect(jsonPath("$.author.nickname").value(stranger.getNickname()))
                .andExpect(jsonPath("$.viewer.canEdit").value(true))
                .andExpect(jsonPath("$.viewer.canDelete").value(true));

        list(null, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.content[0].content").value("반가워요"))
                .andExpect(jsonPath("$.content[1].content").value("방명록 20"))
                .andExpect(jsonPath("$.totalElements").value(22))
                .andExpect(jsonPath("$.totalPages").value(2));
        list(null, 2).andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[1].content").value("방명록 0"));
        list(null, 0).andExpect(status().isBadRequest());
    }

    @Test
    void doubleClickMakesOneEntryAndAnonymousMustLogIn() throws Exception {
        String key = UUID.randomUUID().toString();
        write(visitor, "{\"content\":\"한 번만\"}", key).andExpect(status().isCreated());
        write(visitor, "{\"content\":\"한 번만\"}", key).andExpect(status().isCreated());
        assertThat(count()).isEqualTo(1);

        write(null, "{\"content\":\"" + "가".repeat(1001) + "\"}", UUID.randomUUID().toString())
                .andExpect(status().isUnauthorized());
        write(visitor, "{\"content\":\"   \"}", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
    }

    @Test
    void secretEntryIsSeenOnlyByBlogOwnerAndAuthor() throws Exception {
        write(visitor, "{\"content\":\"주인만 보세요\",\"secret\":true}", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value(true));

        for (Member viewer : new Member[] {owner, visitor}) {
            list(viewer, 1).andExpect(jsonPath("$.content[0].state").value("NORMAL"))
                    .andExpect(jsonPath("$.content[0].content").value("주인만 보세요"));
        }
        for (Member viewer : new Member[] {stranger, null}) {
            list(viewer, 1).andExpect(jsonPath("$.content[0].state").value("SECRET"))
                    .andExpect(jsonPath("$.content[0].content").doesNotExist())
                    .andExpect(jsonPath("$.content[0].author").doesNotExist());
        }
    }

    @Test
    void repliesAreOneLevelAndDeletedParentWithRepliesStaysAsPlaceholder() throws Exception {
        long parentId = idOf(write(visitor, "{\"content\":\"질문\"}", UUID.randomUUID().toString()));
        long replyId = idOf(write(owner, "{\"content\":\"답\",\"parentId\":" + parentId + "}",
                UUID.randomUUID().toString()));
        write(visitor, "{\"content\":\"답의 답\",\"parentId\":" + replyId + "}", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentId"));
        list(null, 1).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].replies[0].content").value("답"));

        remove(parentId, visitor).andExpect(status().isNoContent());
        list(null, 1).andExpect(jsonPath("$.content[0].state").value("DELETED"))
                .andExpect(jsonPath("$.content[0].replies[0].content").value("답"));
        // 답글도 지우면 자리도 사라진다
        remove(replyId, owner).andExpect(status().isNoContent());
        list(null, 1).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void onlyAuthorEditsAndAuthorOrOwnerDeletes() throws Exception {
        long id = idOf(write(visitor, "{\"content\":\"처음\"}", UUID.randomUUID().toString()));

        edit(id, null, "{\"content\":\"x\"}").andExpect(status().isUnauthorized());
        edit(id, owner, "{\"content\":\"주인이 고침\"}").andExpect(status().isForbidden());
        edit(id, visitor, "{\"content\":\"\"}").andExpect(status().isBadRequest());
        edit(id, visitor, "{\"content\":\"고침\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고침"))
                .andExpect(jsonPath("$.updatedAt").isString());

        remove(id, stranger).andExpect(status().isForbidden());
        remove(id, owner).andExpect(status().isNoContent());
        remove(id, owner).andExpect(status().isNotFound());
        edit(id, visitor, "{\"content\":\"되살리기\"}").andExpect(status().isNotFound());
    }

    @Test
    void entryOfAnotherBlogIs404HereAndHiddenBlogHasNoGuestbook() throws Exception {
        Blog other = testBlogs.create(testMembers.create());
        Guestbook elsewhere = guestbookRepository.save(Guestbook.write(other, visitor, "다른 블로그", false));
        edit(elsewhere.getId(), visitor, "{\"content\":\"x\"}").andExpect(status().isNotFound());
        remove(elsewhere.getId(), visitor).andExpect(status().isNotFound());
        write(visitor, "{\"content\":\"답\",\"parentId\":" + elsewhere.getId() + "}", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest());

        testBlogs.restrict(blog);
        list(null, 1).andExpect(status().isNotFound());
    }

    private long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM guestbook WHERE blog_id = ?", Long.class,
                blog.getId());
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions write(Member member, String body, String key) throws Exception {
        return send(post("/api/guestbook").header("Idempotency-Key", key), member, body);
    }

    private ResultActions edit(long id, Member member, String body) throws Exception {
        return send(patch("/api/guestbook/" + id), member, body);
    }

    private ResultActions remove(long id, Member member) throws Exception {
        return send(delete("/api/guestbook/" + id), member, null);
    }

    private ResultActions list(Member member, int page) throws Exception {
        var request = get("/api/guestbook").param("page", String.valueOf(page))
                .header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(member == null ? request : request.cookie(testMembers.loginCookies(member)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Member member, String body) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(member == null ? request : request.cookie(testMembers.loginCookies(member)));
    }

}
