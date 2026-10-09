package com.nhnacademy.blog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
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
 * 글 발행·수정·삭제·공개 범위 (T031~T034, POST-01·02·03·06, spec US2 수용 시나리오 1~5, 7, 8).
 */
class PostWriteIntegrationTest extends IntegrationTestSupport {

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

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ModerationLogRepository moderationLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Blog blog;
    Cookie[] ownerCookies;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        blog = testBlogs.create(owner);
        ownerCookies = testMembers.loginCookies(owner);
    }

    // ---------- 발행 (T031) ----------

    @Test
    void publishSanitizesBodyAndReturnsPostUrl() throws Exception {
        Category category = categoryRepository.save(Category.create(blog, null, "Java", 0));
        String body = """
                {"title":"  첫 글 ","contentHtml":"<h2>제목</h2><p onclick=\\"x()\\">본문 <b>굵게</b></p><script>alert(1)</script>",
                 "categoryId":%d,"topic":"IT_DEV","visibility":"PUBLIC","status":"PUBLISHED"}
                """.formatted(category.getId());

        String response = publish(ownerCookies, body, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(response, "$.id");
        assertThat((String) JsonPath.read(response, "$.url"))
                .isEqualTo("http://" + TestBlogs.host(blog) + "/" + id);
        Post saved = postRepository.findById(id.longValue()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("첫 글");
        assertThat(saved.getContentHtml()).isEqualTo("<h2>제목</h2><p>본문 <b>굵게</b></p>");
        assertThat(saved.getSummary()).isEqualTo("제목 본문 굵게");
        assertThat(saved.getPublishedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        listOf(null).andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].category.name").value("Java"));
    }

    @Test
    void blankTitleIsNotPublished() throws Exception {
        publish(ownerCookies, body("   ", "PUBLIC"), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("제목을 입력해 주세요."));
        publish(ownerCookies, body("가".repeat(201), "PUBLIC"), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest());

        assertThat(postRepository.count(
                (root, query, cb) -> cb.equal(root.get("blog").get("id"), blog.getId()))).isZero();
    }

    @Test
    void sameIdempotencyKeyCreatesOnePost() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = publish(ownerCookies, body("한 번만", "PUBLIC"), key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = publish(ownerCookies, body("한 번만", "PUBLIC"), key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        listOf(ownerCookies).andExpect(jsonPath("$.totalElements").value(1));
        publish(ownerCookies, body("키 없음", "PUBLIC"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void onlyOwnerPublishes() throws Exception {
        publish(null, body("x", "PUBLIC"), UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        publish(testMembers.loginCookies(testMembers.create()), body("x", "PUBLIC"), UUID.randomUUID().toString())
                .andExpect(status().isForbidden());
    }

    @Test
    void unsupportedOptionsAreRejectedNotIgnored() throws Exception {
        publish(ownerCookies, body("x", "SUBSCRIBERS"), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("visibility"));
        publish(ownerCookies, """
                {"title":"x","contentHtml":"","visibility":"PUBLIC","status":"DRAFT","scheduledAt":"2026-12-01T09:00:00"}
                """, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("scheduledAt"));
    }

    @Test
    void categoryOfAnotherBlogIsRejected() throws Exception {
        Category foreign = categoryRepository.save(
                Category.create(testBlogs.create(testMembers.create()), null, "남의 것", 0));

        publish(ownerCookies, """
                {"title":"x","contentHtml":"","categoryId":%d,"visibility":"PUBLIC","status":"PUBLISHED"}
                """.formatted(foreign.getId()), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
    }

    // ---------- 수정 (T032) ----------

    @Test
    void editKeepsPublishedAtAndRecordsUpdatedAt() throws Exception {
        Post post = testPosts.published(blog, Visibility.PUBLIC);
        jdbcTemplate.update("UPDATE post SET published_at = '2026-10-01 09:00:00', updated_at = '2026-10-01 09:00:00'"
                + " WHERE id = ?", post.getId());

        perform(put("/api/posts/" + post.getId()), ownerCookies, body("고친 제목", "PRIVATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(post.getId()))
                .andExpect(jsonPath("$.url").value("http://" + TestBlogs.host(blog) + "/" + post.getId()));

        Post edited = postRepository.findById(post.getId()).orElseThrow();
        assertThat(edited.getTitle()).isEqualTo("고친 제목");
        assertThat(edited.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(edited.getPublishedAt()).hasToString("2026-10-01T09:00");
        assertThat(edited.getUpdatedAt()).isAfter(edited.getPublishedAt());
    }

    @Test
    void othersGet403OnVisiblePostAnd404OnHiddenPost() throws Exception {
        Post publicPost = testPosts.published(blog, Visibility.PUBLIC);
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);
        Cookie[] other = testMembers.loginCookies(testMembers.create());

        perform(put("/api/posts/" + publicPost.getId()), other, body("남의 글", "PUBLIC"))
                .andExpect(status().isForbidden());
        perform(put("/api/posts/" + publicPost.getId()), null, body("남의 글", "PUBLIC"))
                .andExpect(status().isUnauthorized());
        // 볼 수 없는 글은 로그인 여부와 상관없이 존재를 숨긴다 (헌법 원칙 II)
        perform(put("/api/posts/" + privatePost.getId()), other, body("남의 글", "PUBLIC"))
                .andExpect(status().isNotFound());
        perform(delete("/api/posts/" + privatePost.getId()), null, null).andExpect(status().isNotFound());
        perform(get("/api/manage/posts/" + publicPost.getId()), other, null).andExpect(status().isForbidden());
        // 잘못된 본문이어도 권한이 먼저다
        perform(put("/api/posts/" + publicPost.getId()), other, body("", "PUBLIC"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postOfAnotherBlogIs404OnThisBlogAddress() throws Exception {
        // 같은 주인의 다른 블로그 글이어도, 이 블로그 주소의 API에서는 없는 글이다
        Blog otherBlog = testBlogs.create(owner);
        Post postInOtherBlog = testPosts.published(otherBlog, Visibility.PUBLIC);

        perform(put("/api/posts/" + postInOtherBlog.getId()), ownerCookies, body("x", "PUBLIC"))
                .andExpect(status().isNotFound());
        perform(put("/api/posts/999999999"), ownerCookies, body("x", "PUBLIC")).andExpect(status().isNotFound());
    }

    @Test
    void blindedPostCannotBeEditedButCanBeDeleted() throws Exception {
        Post post = testPosts.published(blog, Visibility.PUBLIC);
        testPosts.blind(post);
        moderationLogRepository.save(ModerationLog.record(testMembers.admin(), ModerationAction.BLIND,
                ModerationTargetType.POST, post.getId(), SanctionReason.COPYRIGHT, null));

        perform(put("/api/posts/" + post.getId()), ownerCookies, body("", "PUBLIC"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_BLINDED"))
                .andExpect(jsonPath("$.detail.reason").value("COPYRIGHT"));
        perform(get("/api/manage/posts/" + post.getId()), ownerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blind.reason").value("COPYRIGHT"));
        perform(delete("/api/posts/" + post.getId()), ownerCookies, null).andExpect(status().isNoContent());
    }

    @Test
    void managedPostLoadsEditorFields() throws Exception {
        Category category = categoryRepository.save(Category.create(blog, null, "Java", 0));
        Post post = testPosts.published(blog, category, Visibility.PRIVATE, LocalDateTime.now());

        perform(get("/api/manage/posts/" + post.getId()), ownerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.contentHtml").value("<p>본문</p>"))
                .andExpect(jsonPath("$.categoryId").value(category.getId()))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.blind").doesNotExist());
    }

    // ---------- 공개 범위 (T034) ----------

    @Test
    void privatePostDisappearsForOthers() throws Exception {
        Post post = testPosts.published(blog, Visibility.PUBLIC);
        listOf(null).andExpect(jsonPath("$.totalElements").value(1));

        perform(patch("/api/posts/" + post.getId() + "/visibility"), ownerCookies, "{\"visibility\":\"PRIVATE\"}")
                .andExpect(status().isNoContent());

        listOf(null).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/blog").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.postCount").value(0));
        listOf(ownerCookies).andExpect(jsonPath("$.totalElements").value(1));
        perform(patch("/api/posts/" + post.getId() + "/visibility"), ownerCookies,
                "{\"visibility\":\"SUBSCRIBERS\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- 삭제 (T033) ----------

    @Test
    void deleteHidesPostAndCleansCommentsLikesNotifications() throws Exception {
        Post post = testPosts.published(blog, Visibility.PUBLIC);
        Member reader = testMembers.create();
        jdbcTemplate.update("INSERT INTO comment (post_id, member_id, content) VALUES (?, ?, '댓글')",
                post.getId(), reader.getId());
        Long commentId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM comment WHERE post_id = ?", Long.class,
                post.getId());
        jdbcTemplate.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", post.getId(), reader.getId());
        jdbcTemplate.update("INSERT INTO notification (receiver_id, type, target_type, target_id, message)"
                + " VALUES (?, 'LIKE', 'POST', ?, '공감'), (?, 'COMMENT', 'COMMENT', ?, '댓글')",
                owner.getId(), post.getId(), owner.getId(), commentId);

        perform(delete("/api/posts/" + post.getId()), ownerCookies, null).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM post WHERE id = ?",
                Boolean.class, post.getId())).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM comment WHERE id = ?",
                Boolean.class, commentId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?",
                Integer.class, post.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE receiver_id = ?",
                Integer.class, owner.getId())).isZero();
        listOf(ownerCookies).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/blog/sidebar").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.modules[4].data").isEmpty());
        // 지운 글은 다시 지울 수 없다(없는 글)
        perform(delete("/api/posts/" + post.getId()), ownerCookies, null).andExpect(status().isNotFound());
    }

    private String body(String title, String visibility) {
        return """
                {"title":"%s","contentHtml":"<p>본문</p>","visibility":"%s","status":"PUBLISHED"}
                """.formatted(title, visibility);
    }

    private ResultActions publish(Cookie[] cookies, String body, String key) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/posts");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return perform(request, cookies, body);
    }

    private ResultActions listOf(Cookie[] cookies) throws Exception {
        var request = get("/api/posts").header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Cookie[] cookies, String body)
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
