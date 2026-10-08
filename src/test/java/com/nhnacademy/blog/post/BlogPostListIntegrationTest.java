package com.nhnacademy.blog.post;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 블로그 메인 글 목록 (T024, BLOG-03, spec US1 수용 시나리오 8, 목록과 페이지).
 */
class BlogPostListIntegrationTest extends IntegrationTestSupport {

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

    Member owner;
    Blog blog;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        blog = testBlogs.create(owner);
    }

    @Test
    void emptyBlogHasEmptyFirstPage() throws Exception {
        list(null, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void latestFirstTenPerPageAndTieBrokenById() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 9, 0);
        Post[] posts = new Post[12];
        for (int i = 0; i < 12; i++) {
            posts[i] = testPosts.published(blog, null, Visibility.PUBLIC, base.plusHours(i));
        }
        // 발행 시각이 같으면 나중에 만든 글이 위다
        Post sameTime = testPosts.published(blog, null, Visibility.PUBLIC, base.plusHours(11));

        list(null, "")
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalElements").value(13))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(sameTime.getId()))
                .andExpect(jsonPath("$.content[1].id").value(posts[11].getId()))
                .andExpect(jsonPath("$.content[0].blog.address").value(blog.getAddress()))
                .andExpect(jsonPath("$.content[0].category").doesNotExist())
                .andExpect(jsonPath("$.content[0].publishedAt").value("2026-10-01T20:00:00+09:00"));
        list(null, "?page=2")
                .andExpect(jsonPath("$.content[*].id", contains(posts[2].getId().intValue(),
                        posts[1].getId().intValue(), posts[0].getId().intValue())));
        list(null, "?page=999").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        list(null, "?size=51").andExpect(status().isBadRequest());
        list(null, "?page=0").andExpect(status().isBadRequest());
    }

    @Test
    void othersSeeOnlyVisiblePostsAndOwnerSeesAllPublished() throws Exception {
        Post publicPost = testPosts.published(blog, Visibility.PUBLIC);
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);
        Post blinded = testPosts.published(blog, Visibility.PUBLIC);
        testPosts.blind(blinded);
        testPosts.delete(testPosts.published(blog, Visibility.PUBLIC));
        testPosts.draft(blog);
        testPosts.published(testBlogs.create(testMembers.create()), Visibility.PUBLIC);

        list(null, "").andExpect(jsonPath("$.content[*].id", contains(publicPost.getId().intValue())));
        list(testMembers.loginCookies(testMembers.create()), "")
                .andExpect(jsonPath("$.totalElements").value(1));
        // 주인: 비공개·숨긴 글까지 발행 글 전부. 임시저장과 삭제한 글은 없다
        list(testMembers.loginCookies(owner), "")
                .andExpect(jsonPath("$.content[*].id", contains(blinded.getId().intValue(),
                        privatePost.getId().intValue(), publicPost.getId().intValue())));
    }

    @Test
    void filtersByCategoryIncludingChildrenAndUncategorized() throws Exception {
        Category dev = categoryRepository.save(Category.create(blog, null, "개발", 0));
        Category spring = categoryRepository.save(Category.create(blog, dev, "Spring", 0));
        LocalDateTime now = LocalDateTime.now();
        Post inDev = testPosts.published(blog, dev, Visibility.PUBLIC, now.minusHours(3));
        Post inSpring = testPosts.published(blog, spring, Visibility.PUBLIC, now.minusHours(2));
        Post uncategorized = testPosts.published(blog, null, Visibility.PUBLIC, now.minusHours(1));

        list(null, "?categoryId=" + dev.getId())
                .andExpect(jsonPath("$.content[*].id", contains(inSpring.getId().intValue(),
                        inDev.getId().intValue())))
                .andExpect(jsonPath("$.content[0].category.name").value("Spring"));
        list(null, "?categoryId=" + spring.getId())
                .andExpect(jsonPath("$.content[*].id", contains(inSpring.getId().intValue())));
        list(null, "?categoryId=0")
                .andExpect(jsonPath("$.content[*].id", contains(uncategorized.getId().intValue())));
    }

    @Test
    void categoryOfAnotherBlogIs404() throws Exception {
        Blog otherBlog = testBlogs.create(testMembers.create());
        Category otherCategory = categoryRepository.save(Category.create(otherBlog, null, "남의 것", 0));

        list(null, "?categoryId=" + otherCategory.getId()).andExpect(status().isNotFound());
        list(null, "?categoryId=-5").andExpect(status().isNotFound());
    }

    @Test
    void hiddenBlogIs404() throws Exception {
        testPosts.published(blog, Visibility.PUBLIC);
        testBlogs.restrict(blog);

        list(null, "").andExpect(status().isNotFound());
        list(testMembers.loginCookies(owner), "").andExpect(status().isOk());
    }

    private ResultActions list(Cookie[] cookies, String query) throws Exception {
        var request = get("/api/posts" + query).header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
