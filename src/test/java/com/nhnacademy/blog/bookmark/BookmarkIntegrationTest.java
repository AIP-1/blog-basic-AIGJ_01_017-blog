package com.nhnacademy.blog.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.junit.jupiter.api.Test;

/** 저장한 글 (스텝 20: T074~T077, SOC-03, spec US16 1~4). */
class BookmarkIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void saveIsIdempotentAndListShowsInvisiblePostsBySnapshot() throws Exception {
        Member owner = testMembers.create();
        Member reader = testMembers.create();
        Blog blog = testBlogs.createPrimary(owner);
        Cookie[] ownerCookies = testMembers.loginCookies(owner);
        Cookie[] readerCookies = testMembers.loginCookies(reader);
        long first = publish(blog, ownerCookies, "처음 저장할 글");
        long second = publish(blog, ownerCookies, "나중에 숨겨질 글");

        send(put("/api/posts/" + first + "/bookmark"), blog.getAddress(), null, null).andExpect(status().isUnauthorized());
        send(put("/api/posts/" + first + "/bookmark"), blog.getAddress(), readerCookies, null)
                .andExpect(jsonPath("$.bookmarked").value(true));
        send(put("/api/posts/" + first + "/bookmark"), blog.getAddress(), readerCookies, null).andExpect(status().isOk());
        send(put("/api/posts/" + second + "/bookmark"), blog.getAddress(), readerCookies, null).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_bookmark WHERE member_id = ?", Integer.class,
                reader.getId())).isEqualTo(2);
        send(get("/api/posts/" + first), blog.getAddress(), readerCookies, null)
                .andExpect(jsonPath("$.viewer.bookmarked").value(true));

        // 주인이 둘째 글을 비공개로 바꾸고 제목도 바꾼다 → 저장한 사람에게는 저장할 때의 제목으로 "볼 수 없는 글"
        send(patch("/api/posts/" + second + "/visibility"), blog.getAddress(), ownerCookies,
                "{\"visibility\":\"PRIVATE\"}").andExpect(status().isNoContent());
        jdbcTemplate.update("UPDATE post SET title = '바뀐 제목' WHERE id = ?", second);

        send(get("/api/me/bookmarks"), null, readerCookies, null)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].visible").value(false))
                .andExpect(jsonPath("$.content[0].postId").value(second))
                .andExpect(jsonPath("$.content[0].titleSnapshot").value("나중에 숨겨질 글"))
                .andExpect(jsonPath("$.content[0].blogNameSnapshot").value(blog.getName()))
                .andExpect(jsonPath("$.content[0].post").doesNotExist())
                .andExpect(jsonPath("$.content[1].visible").value(true))
                .andExpect(jsonPath("$.content[1].post.title").value("처음 저장할 글"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        // 주소로 열면 404, 목록에서는 바로 취소된다
        send(get("/api/posts/" + second), blog.getAddress(), readerCookies, null).andExpect(status().isNotFound());
        send(delete("/api/me/bookmarks/" + second), null, readerCookies, null).andExpect(status().isNoContent());
        send(get("/api/me/bookmarks"), null, readerCookies, null).andExpect(jsonPath("$.content", hasSize(1)));
        // 다시 볼 수 있게 되면 지금 제목으로
        send(delete("/api/posts/" + first + "/bookmark"), blog.getAddress(), readerCookies, null)
                .andExpect(jsonPath("$.bookmarked").value(false));
        send(get("/api/me/bookmarks"), null, ownerCookies, null).andExpect(jsonPath("$.content", hasSize(0)));
        send(get("/api/me/bookmarks"), null, null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void bookmarksPageByTwenty() throws Exception {
        Member owner = testMembers.create();
        Member reader = testMembers.create();
        Blog blog = testBlogs.createPrimary(owner);
        Cookie[] ownerCookies = testMembers.loginCookies(owner);
        Cookie[] readerCookies = testMembers.loginCookies(reader);
        for (int i = 0; i < 21; i++) {
            long id = publish(blog, ownerCookies, "글 " + i);
            send(put("/api/posts/" + id + "/bookmark"), blog.getAddress(), readerCookies, null).andExpect(status().isOk());
        }
        String first = send(get("/api/me/bookmarks"), null, readerCookies, null)
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");
        send(get("/api/me/bookmarks").param("cursor", cursor), null, readerCookies, null)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].post.title").value("글 0"));
    }

    private long publish(Blog target, Cookie[] cookies, String title) throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                target.getAddress(), cookies,
                "{\"title\":\"" + title + "\",\"contentHtml\":\"<p>본문</p>\",\"visibility\":\"PUBLIC\",\"status\":\"PUBLISHED\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String blogAddress, Cookie[] cookies, String body)
            throws Exception {
        // 블로그 API면 블로그 화면처럼 X-Blog-Address로 블로그를 알린다(null이면 플랫폼)
        if (blogAddress != null) {
            request.header(TestBlogs.HEADER, blogAddress);
        }
        request.header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
