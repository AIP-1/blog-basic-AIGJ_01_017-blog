package com.nhnacademy.blog.post;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.PostStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 임시저장 (T059, POST-08, spec US5 수용 시나리오 1): 제목 없이 저장, 다시 저장, 주인에게만 보임, 불러와 발행.
 */
class DraftIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    @Autowired
    PostRepository postRepository;

    Blog blog;
    Cookie[] ownerCookies;
    Cookie[] otherCookies;

    @BeforeEach
    void setUp() {
        Member owner = testMembers.create();
        blog = testBlogs.create(owner);
        ownerCookies = testMembers.loginCookies(owner);
        otherCookies = testMembers.loginCookies(testMembers.create());
    }

    @Test
    void draftWithoutTitleIsSavedAndOnlyOwnerSeesIt() throws Exception {
        long id = createDraft("", "<p>쓰는 중</p>");

        Post saved = postRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(saved.getTitle()).isEmpty();
        assertThat(saved.getPublishedAt()).isNull();

        // 블로그 화면의 글 목록에는 주인에게도 나오지 않는다
        send(get("/api/posts"), ownerCookies, null).andExpect(jsonPath("$.totalElements").value(0));
        // 다른 사람과 비회원에게는 없는 글이다
        send(get("/api/posts/" + id), otherCookies, null).andExpect(status().isNotFound());
        send(get("/api/posts/" + id), null, null).andExpect(status().isNotFound());
        send(get("/api/manage/posts/" + id), otherCookies, null).andExpect(status().isNotFound());
        // 주인은 임시저장 목록에서 찾아 불러온다
        send(get("/api/manage/posts?status=DRAFT"), ownerCookies, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
        send(get("/api/manage/posts/" + id), ownerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.contentHtml").value("<p>쓰는 중</p>"));
    }

    @Test
    void draftIsSavedAgainThenPublished() throws Exception {
        long id = createDraft("", "<p>처음</p>");

        // 자동 저장과 같은 다시 저장: 임시저장 그대로, 발행 시각 없음
        save(id, "", "<p>이어 쓴 본문</p>", "DRAFT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(postRepository.findById(id).orElseThrow().getPublishedAt()).isNull();

        // 발행은 제목이 있어야 한다. 실패해도 임시저장 글은 그대로다
        save(id, "  ", "<p>이어 쓴 본문</p>", "PUBLISHED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
        assertThat(postRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);

        save(id, "다 쓴 글", "<p>이어 쓴 본문</p>", "PUBLISHED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        Post published = postRepository.findById(id).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        send(get("/api/posts/" + id), otherCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("다 쓴 글"));
        send(get("/api/posts"), null, null).andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void publishedPostCannotGoBackToDraft() throws Exception {
        Post post = testPosts.published(blog, Visibility.PUBLIC);

        save(post.getId(), "제목", "<p>본문</p>", "DRAFT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void onlyOwnerSavesDrafts() throws Exception {
        long id = createDraft("", "<p>x</p>");

        send(put("/api/posts/" + id), otherCookies, body("", "<p>남이 고침</p>", "DRAFT"))
                .andExpect(status().isNotFound());
        send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), otherCookies,
                body("", "<p>x</p>", "DRAFT"))
                .andExpect(status().isForbidden());
    }

    private long createDraft(String title, String html) throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                ownerCookies, body(title, html, "DRAFT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions save(long id, String title, String html, String status) throws Exception {
        return send(put("/api/posts/" + id), ownerCookies, body(title, html, status));
    }

    private static String body(String title, String html, String status) {
        return """
                {"title":"%s","contentHtml":"%s","visibility":"PUBLIC","status":"%s"}
                """.formatted(title, html, status);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (cookies != null) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

}
