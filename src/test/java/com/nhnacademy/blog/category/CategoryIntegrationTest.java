package com.nhnacademy.blog.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
 * 카테고리 추가·이름 변경·삭제 (T029, T040, CAT-01, spec US2 수용 시나리오 9).
 */
class CategoryIntegrationTest extends IntegrationTestSupport {

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
    void ownerAddsCategoriesAtTheBottom() throws Exception {
        create(ownerCookies, "{\"name\":\"  개발 \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("개발"))
                .andExpect(jsonPath("$.sortOrder").value(0));
        create(ownerCookies, "{\"name\":\"여행\"}").andExpect(jsonPath("$.sortOrder").value(1));

        mockMvc.perform(get("/api/categories").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[*].name", contains("개발", "여행")));
    }

    @Test
    void duplicateNameIs409IgnoringCase() throws Exception {
        create(ownerCookies, "{\"name\":\"Java\"}").andExpect(status().isCreated());

        create(ownerCookies, "{\"name\":\"Java\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"));
        create(ownerCookies, "{\"name\":\"java\"}").andExpect(status().isConflict());
        // 다른 블로그에는 같은 이름이 있어도 된다
        Member other = testMembers.create();
        Blog otherBlog = testBlogs.create(other);
        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.HOST, TestBlogs.host(otherBlog))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java\"}")
                        .cookie(testMembers.loginCookies(other)))
                .andExpect(status().isCreated());
    }

    @Test
    void nameMustBe1To30AndNoParentYet() throws Exception {
        create(ownerCookies, "{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        create(ownerCookies, "{\"name\":\"" + "가".repeat(31) + "\"}").andExpect(status().isBadRequest());
        create(ownerCookies, "{\"name\":\"" + "가".repeat(30) + "\"}").andExpect(status().isCreated());
        create(ownerCookies, "{\"name\":\"하위\",\"parentId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentId"));
    }

    @Test
    void onlyOwnerChangesCategories() throws Exception {
        Category category = categoryRepository.save(Category.create(blog, null, "개발", 0));
        Cookie[] otherCookies = testMembers.loginCookies(testMembers.create());

        create(null, "{\"name\":\"x\"}").andExpect(status().isUnauthorized());
        create(otherCookies, "{\"name\":\"x\"}").andExpect(status().isForbidden());
        perform(patch("/api/categories/" + category.getId()), otherCookies, "{\"name\":\"\"}")
                .andExpect(status().isForbidden());
        perform(delete("/api/categories/" + category.getId()), otherCookies, null)
                .andExpect(status().isForbidden());
        perform(delete("/api/categories/" + category.getId()), null, null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renameKeepsPostsAndRejectsDuplicates() throws Exception {
        Category dev = categoryRepository.save(Category.create(blog, null, "개발", 0));
        categoryRepository.save(Category.create(blog, null, "여행", 1));

        perform(patch("/api/categories/" + dev.getId()), ownerCookies, "{\"name\":\"프로그래밍\"}")
                .andExpect(status().isNoContent());
        perform(patch("/api/categories/" + dev.getId()), ownerCookies, "{\"name\":\"프로그래밍\"}")
                .andExpect(status().isNoContent());
        perform(patch("/api/categories/" + dev.getId()), ownerCookies, "{\"name\":\"여행\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"));

        assertThat(categoryRepository.findById(dev.getId()).orElseThrow().getName()).isEqualTo("프로그래밍");
    }

    @Test
    void deletingMovesPostsToUncategorizedWithoutTouchingUpdatedAt() throws Exception {
        Category category = categoryRepository.save(Category.create(blog, null, "A", 0));
        Post first = testPosts.published(blog, category, Visibility.PUBLIC, LocalDateTime.now().minusHours(2));
        Post second = testPosts.published(blog, category, Visibility.PRIVATE, LocalDateTime.now().minusHours(1));
        jdbcTemplate.update("UPDATE post SET updated_at = '2026-01-01 00:00:00' WHERE blog_id = ?", blog.getId());

        perform(delete("/api/categories/" + category.getId()), ownerCookies, null).andExpect(status().isNoContent());

        assertThat(categoryRepository.existsById(category.getId())).isFalse();
        assertThat(jdbcTemplate.queryForList(
                "SELECT category_id FROM post WHERE id IN (?, ?)", Long.class, first.getId(), second.getId()))
                .containsOnlyNulls();
        assertThat(jdbcTemplate.queryForList(
                "SELECT CAST(updated_at AS CHAR) FROM post WHERE blog_id = ?", String.class, blog.getId()))
                .allSatisfy(updatedAt -> assertThat(updatedAt).startsWith("2026-01-01 00:00:00"));
        mockMvc.perform(get("/api/categories").header(HttpHeaders.HOST, TestBlogs.host(blog)).cookie(ownerCookies))
                .andExpect(jsonPath("$.uncategorizedCount").value(2))
                .andExpect(jsonPath("$.categories").isEmpty());
    }

    @Test
    void categoryWithChildrenCannotBeDeleted() throws Exception {
        Category parent = categoryRepository.save(Category.create(blog, null, "개발", 0));
        categoryRepository.save(Category.create(blog, parent, "Spring", 0));

        perform(delete("/api/categories/" + parent.getId()), ownerCookies, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_CHILDREN"));
    }

    @Test
    void categoryOfAnotherBlogIs404() throws Exception {
        Blog otherBlog = testBlogs.create(testMembers.create());
        Category foreign = categoryRepository.save(Category.create(otherBlog, null, "남의 것", 0));

        perform(patch("/api/categories/" + foreign.getId()), ownerCookies, "{\"name\":\"내 것\"}")
                .andExpect(status().isNotFound());
        perform(delete("/api/categories/" + foreign.getId()), ownerCookies, null)
                .andExpect(status().isNotFound());
        assertThat(categoryRepository.existsById(foreign.getId())).isTrue();
    }

    @Test
    void createdIdIsUsableForPostFilter() throws Exception {
        String body = create(ownerCookies, "{\"name\":\"Java\"}").andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/posts").param("categoryId", id.toString())
                        .header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isOk());
    }

    private ResultActions create(Cookie[] cookies, String body) throws Exception {
        return perform(post("/api/categories"), cookies, body);
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
