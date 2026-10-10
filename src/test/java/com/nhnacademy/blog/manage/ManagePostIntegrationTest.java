package com.nhnacademy.blog.manage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
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
 * 내 글 관리 (T067, MNG-01, spec US8 시나리오 1·2): 거르기·검색·20개씩, 일괄 공개 범위 변경·삭제.
 */
class ManagePostIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    @Autowired
    CategoryRepository categoryRepository;

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

    @Test
    void onlyOwnerCanUseIt() throws Exception {
        Cookie[] stranger = testMembers.loginCookies(testMembers.create());
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                get("/api/manage/posts"), patch("/api/manage/posts"), delete("/api/manage/posts")}) {
            send(request, null, "{}").andExpect(status().isUnauthorized());
        }
        send(get("/api/manage/posts").param("status", "이상한값"), stranger, null).andExpect(status().isForbidden());
        send(patch("/api/manage/posts"), stranger, "{}").andExpect(status().isForbidden());
        send(delete("/api/manage/posts"), stranger, "{}").andExpect(status().isForbidden());
    }

    @Test
    void listsAllMyPostsNewestFirstWithStateAndBlindReason() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        Post older = titled(testPosts.published(blog, null, Visibility.PUBLIC, base), "오래된 글");
        Post privatePost = titled(testPosts.published(blog, null, Visibility.PRIVATE, base.plusHours(1)), "비공개 글");
        Post blinded = titled(testPosts.published(blog, null, Visibility.PUBLIC, base.plusHours(2)), "숨긴 글");
        testPosts.blind(blinded);
        jdbcTemplate.update("INSERT INTO moderation_log (admin_id, action, target_type, target_id, reason) "
                + "VALUES (?, 'BLIND', 'POST', ?, 'COPYRIGHT')", testMembers.admin().getId(), blinded.getId());
        Post draft = titled(testPosts.draft(blog), "임시 글");
        testPosts.delete(testPosts.published(blog, Visibility.PUBLIC));
        testPosts.published(testBlogs.create(testMembers.create()), Visibility.PUBLIC);

        send(get("/api/manage/posts"), ownerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.size").value(20))
                // 임시저장은 수정 시각(지금)이라 맨 위, 나머지는 발행 시각 최신순
                .andExpect(jsonPath("$.content[*].id", contains(draft.getId().intValue(),
                        blinded.getId().intValue(), privatePost.getId().intValue(), older.getId().intValue())))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[1].blinded").value(true))
                .andExpect(jsonPath("$.content[1].blind.reason").value("COPYRIGHT"))
                .andExpect(jsonPath("$.content[1].blind.reasonMessage").value("저작권 침해"))
                .andExpect(jsonPath("$.content[2].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.content[3].blind").doesNotExist());
    }

    @Test
    void filtersByStatusVisibilityCategoryAndTitle() throws Exception {
        Category parent = categoryRepository.save(Category.create(blog, null, "개발", 0));
        Category child = categoryRepository.save(Category.create(blog, parent, "Spring", 0));
        titled(testPosts.published(blog, parent, Visibility.PUBLIC, LocalDateTime.now()), "상위 글");
        titled(testPosts.published(blog, child, Visibility.PRIVATE, LocalDateTime.now()), "하위 글 100%");
        titled(testPosts.published(blog, null, Visibility.PUBLIC, LocalDateTime.now()), "미분류 1000");
        testPosts.draft(blog);

        list("status", "DRAFT").andExpect(jsonPath("$.totalElements").value(1));
        list("status", "PUBLISHED").andExpect(jsonPath("$.totalElements").value(3));
        list("visibility", "PRIVATE").andExpect(jsonPath("$.content[*].title", contains("하위 글 100%")));
        list("categoryId", parent.getId().toString())
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("상위 글", "하위 글 100%")));
        list("categoryId", child.getId().toString()).andExpect(jsonPath("$.totalElements").value(1));
        list("categoryId", "0").andExpect(jsonPath("$.totalElements").value(2));
        // %는 글자 그대로 찾는다(모든 글이 아니라 "100%"가 든 제목만)
        list("q", "100%").andExpect(jsonPath("$.content[*].title", contains("하위 글 100%")));
        list("q", "  ").andExpect(jsonPath("$.totalElements").value(4));

        list("status", "BOGUS").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
        Category foreign = categoryRepository.save(
                Category.create(testBlogs.create(testMembers.create()), null, "남의 것", 0));
        list("categoryId", foreign.getId().toString()).andExpect(status().isNotFound());
    }

    @Test
    void pagesOfTwenty() throws Exception {
        for (int i = 0; i < 25; i++) {
            testPosts.published(blog, null, Visibility.PUBLIC, LocalDateTime.now().minusMinutes(i));
        }
        list("page", "2").andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void bulkVisibilityChangesOnlyMyPosts() throws Exception {
        Post first = testPosts.published(blog, Visibility.PUBLIC);
        Post second = testPosts.published(blog, Visibility.PUBLIC);
        Post deleted = testPosts.published(blog, Visibility.PUBLIC);
        testPosts.delete(deleted);
        Post foreign = testPosts.published(testBlogs.create(testMembers.create()), Visibility.PUBLIC);

        send(patch("/api/manage/posts"), ownerCookies, "{\"postIds\":[%d,%d,%d,%d,999999999],\"visibility\":\"PRIVATE\"}"
                .formatted(first.getId(), second.getId(), deleted.getId(), foreign.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));
        assertThat(visibility(first)).isEqualTo("PRIVATE");
        assertThat(visibility(second)).isEqualTo("PRIVATE");
        assertThat(visibility(foreign)).isEqualTo("PUBLIC");
        // 다른 사람에게는 블로그 목록에서 바로 빠진다
        mockMvc.perform(get("/api/posts").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // 구독자 공개도 일괄로 바꿀 수 있다(스텝 16, POST-12)
        send(patch("/api/manage/posts"), ownerCookies, "{\"postIds\":[%d],\"visibility\":\"SUBSCRIBERS\"}"
                .formatted(first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));
        assertThat(visibility(first)).isEqualTo("SUBSCRIBERS");
        send(patch("/api/manage/posts"), ownerCookies, "{\"postIds\":[],\"visibility\":\"PUBLIC\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("postIds"));
    }

    @Test
    void bulkDeleteRemovesPostsWithTheirCommentsAndLikes() throws Exception {
        Post first = testPosts.published(blog, Visibility.PUBLIC);
        Post second = testPosts.published(blog, Visibility.PUBLIC);
        Post kept = testPosts.published(blog, Visibility.PUBLIC);
        Member reader = testMembers.create();
        jdbcTemplate.update("INSERT INTO comment (post_id, member_id, content) VALUES (?, ?, '댓글')",
                first.getId(), reader.getId());
        jdbcTemplate.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", second.getId(), reader.getId());
        Post foreign = testPosts.published(testBlogs.create(testMembers.create()), Visibility.PUBLIC);

        send(delete("/api/manage/posts"), ownerCookies, "{\"postIds\":[%d,%d,%d]}"
                .formatted(first.getId(), second.getId(), foreign.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(2));

        assertThat(jdbcTemplate.queryForList("SELECT id FROM post WHERE blog_id = ? AND deleted_at IS NULL",
                Long.class, blog.getId())).containsExactly(kept.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment WHERE post_id = ? AND deleted_at IS NULL",
                Integer.class, first.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?",
                Integer.class, second.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NULL FROM post WHERE id = ?",
                Boolean.class, foreign.getId())).isTrue();
        // 다시 지우면 이미 지운 글이라 0
        send(delete("/api/manage/posts"), ownerCookies, "{\"postIds\":[%d]}".formatted(first.getId()))
                .andExpect(jsonPath("$.deletedCount").value(0));
    }

    private Post titled(Post post, String title) {
        jdbcTemplate.update("UPDATE post SET title = ? WHERE id = ?", title, post.getId());
        return post;
    }

    private String visibility(Post post) {
        return jdbcTemplate.queryForObject("SELECT visibility FROM post WHERE id = ?", String.class, post.getId());
    }

    private ResultActions list(String param, String value) throws Exception {
        return send(get("/api/manage/posts").param(param, value), ownerCookies, null);
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
