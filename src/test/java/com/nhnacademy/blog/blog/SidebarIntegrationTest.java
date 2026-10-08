package com.nhnacademy.blog.blog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 사이드바 (T025, BLOG-04, spec US1 수용 시나리오 8).
 */
class SidebarIntegrationTest extends IntegrationTestSupport {

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
    CommentRepository commentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member reader;
    Blog blog;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        reader = testMembers.create();
        blog = testBlogs.create(owner);
    }

    @Test
    void newBlogShowsDefaultModulesInOrder() throws Exception {
        sidebar(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[*].type",
                        contains("PROFILE", "CATEGORY", "TAG", "RECENT_POST", "RECENT_COMMENT")))
                .andExpect(jsonPath("$.modules[0].data.name").value(blog.getName()))
                .andExpect(jsonPath("$.modules[1].data.totalCount").value(0))
                .andExpect(jsonPath("$.modules[1].data.uncategorizedCount").value(0))
                .andExpect(jsonPath("$.modules[1].data.categories", hasSize(0)))
                .andExpect(jsonPath("$.modules[2].data", hasSize(0)))
                .andExpect(jsonPath("$.modules[3].data", hasSize(0)))
                .andExpect(jsonPath("$.modules[4].data", hasSize(0)));
    }

    @Test
    void categoriesInOrderWithVisiblePostCounts() throws Exception {
        categoryRepository.save(Category.create(blog, null, "여행", 1));
        Category dev = categoryRepository.save(Category.create(blog, null, "개발", 0));
        Category spring = categoryRepository.save(Category.create(blog, dev, "Spring", 0));
        LocalDateTime now = LocalDateTime.now();
        testPosts.published(blog, dev, Visibility.PUBLIC, now);
        testPosts.published(blog, spring, Visibility.PUBLIC, now);
        testPosts.published(blog, spring, Visibility.PRIVATE, now);
        testPosts.published(blog, null, Visibility.PUBLIC, now);

        sidebar(null)
                .andExpect(jsonPath("$.modules[1].data.totalCount").value(3))
                .andExpect(jsonPath("$.modules[1].data.uncategorizedCount").value(1))
                .andExpect(jsonPath("$.modules[1].data.categories[*].name", contains("개발", "여행")))
                // 상위 카테고리 글 수는 하위 글을 포함한다
                .andExpect(jsonPath("$.modules[1].data.categories[0].postCount").value(2))
                .andExpect(jsonPath("$.modules[1].data.categories[0].children[0].name").value("Spring"))
                .andExpect(jsonPath("$.modules[1].data.categories[0].children[0].postCount").value(1))
                .andExpect(jsonPath("$.modules[1].data.categories[1].postCount").value(0))
                .andExpect(jsonPath("$.modules[1].data.categories[0].isPrivate").doesNotExist());
        sidebar(testMembers.loginCookies(owner))
                .andExpect(jsonPath("$.modules[1].data.totalCount").value(4))
                .andExpect(jsonPath("$.modules[1].data.categories[0].children[0].postCount").value(2))
                .andExpect(jsonPath("$.modules[1].data.categories[0].isPrivate").value(false));
    }

    @Test
    void privateCategoryIsHiddenFromOthers() throws Exception {
        Category secret = categoryRepository.save(Category.create(blog, null, "일기", 0));
        jdbcTemplate.update("UPDATE category SET is_private = 1 WHERE id = ?", secret.getId());

        sidebar(null).andExpect(jsonPath("$.modules[1].data.categories", hasSize(0)));
        sidebar(testMembers.loginCookies(owner))
                .andExpect(jsonPath("$.modules[1].data.categories[0].isPrivate").value(true));
    }

    @Test
    void recentPostsAreLatestFiveVisibleOnes() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        Post[] posts = new Post[6];
        for (int i = 0; i < 6; i++) {
            posts[i] = testPosts.published(blog, null, Visibility.PUBLIC, base.plusMinutes(i));
        }
        Post privatePost = testPosts.published(blog, null, Visibility.PRIVATE, base.plusMinutes(10));

        sidebar(null).andExpect(jsonPath("$.modules[3].data[*].id", contains(
                posts[5].getId().intValue(), posts[4].getId().intValue(), posts[3].getId().intValue(),
                posts[2].getId().intValue(), posts[1].getId().intValue())));
        sidebar(testMembers.loginCookies(owner))
                .andExpect(jsonPath("$.modules[3].data[0].id").value(privatePost.getId()))
                .andExpect(jsonPath("$.modules[3].data", hasSize(5)));
    }

    @Test
    void recentCommentsSkipHiddenPostsAndMaskSecretOrBlinded() throws Exception {
        Post publicPost = testPosts.published(blog, Visibility.PUBLIC);
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);
        Comment normal = commentRepository.save(Comment.write(publicPost, reader, "잘 읽었습니다", false));
        Comment secret = commentRepository.save(Comment.write(publicPost, reader, "비밀 이야기", true));
        Comment blinded = commentRepository.save(Comment.write(publicPost, reader, "광고", false));
        Comment deleted = commentRepository.save(Comment.write(publicPost, reader, "지운 댓글", false));
        Comment onPrivate = commentRepository.save(Comment.write(privatePost, owner, "메모", false));
        jdbcTemplate.update("UPDATE comment SET is_blinded = 1 WHERE id = ?", blinded.getId());
        jdbcTemplate.update("UPDATE comment SET deleted_at = NOW() WHERE id = ?", deleted.getId());
        // 같은 시각에 들어가도 순서가 정해지게 작성 시각을 벌린다 (normal이 가장 오래됨)
        writtenMinutesAgo(normal, 4);
        writtenMinutesAgo(secret, 3);
        writtenMinutesAgo(blinded, 2);
        writtenMinutesAgo(onPrivate, 1);

        // 비공개 글의 댓글은 다른 사람의 사이드바에 나오지 않는다
        sidebar(null)
                .andExpect(jsonPath("$.modules[4].data[*].id", contains(blinded.getId().intValue(),
                        secret.getId().intValue(), normal.getId().intValue())))
                .andExpect(jsonPath("$.modules[4].data[0].state").value("BLINDED"))
                .andExpect(jsonPath("$.modules[4].data[0].content").value(nullValue()))
                .andExpect(jsonPath("$.modules[4].data[1].state").value("SECRET"))
                .andExpect(jsonPath("$.modules[4].data[1].content").value(nullValue()))
                .andExpect(jsonPath("$.modules[4].data[1].authorNickname").value(nullValue()))
                .andExpect(jsonPath("$.modules[4].data[2].state").value("NORMAL"))
                .andExpect(jsonPath("$.modules[4].data[2].content").value("잘 읽었습니다"))
                .andExpect(jsonPath("$.modules[4].data[2].authorNickname").value(reader.getNickname()))
                .andExpect(jsonPath("$.modules[4].data[2].postId").value(publicPost.getId()));
        sidebar(testMembers.loginCookies(owner))
                .andExpect(jsonPath("$.modules[4].data[0].id").value(onPrivate.getId()))
                .andExpect(jsonPath("$.modules[4].data", hasSize(4)));
    }

    private void writtenMinutesAgo(Comment comment, int minutes) {
        jdbcTemplate.update("UPDATE comment SET created_at = NOW(6) - INTERVAL ? MINUTE WHERE id = ?", minutes,
                comment.getId());
    }

    private ResultActions sidebar(Cookie[] cookies) throws Exception {
        var request = get("/api/blog/sidebar").header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
