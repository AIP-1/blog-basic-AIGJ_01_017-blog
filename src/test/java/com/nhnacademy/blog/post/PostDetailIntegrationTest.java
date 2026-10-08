package com.nhnacademy.blog.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 글 상세 (T041, POST-04)와 이전·다음 글 (T057, POST-10). spec US3 시나리오 3, US4 시나리오 3.
 */
class PostDetailIntegrationTest extends IntegrationTestSupport {

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
    ModerationLogRepository moderationLogRepository;

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
    void anyoneReadsPublicPost() throws Exception {
        Category category = categoryRepository.save(Category.create(blog, null, "Spring", 0));
        Post post = testPosts.published(blog, category, Visibility.PUBLIC, LocalDateTime.of(2026, 10, 8, 9, 0));

        detail(post, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(post.getId()))
                .andExpect(jsonPath("$.blog.address").value(blog.getAddress()))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.contentHtml").value("<p>본문</p>"))
                .andExpect(jsonPath("$.category.name").value("Spring"))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andExpect(jsonPath("$.publishedAt").value("2026-10-08T09:00:00+09:00"))
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.author.id").value(owner.getId()))
                .andExpect(jsonPath("$.author.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.viewer.isOwner").value(false))
                .andExpect(jsonPath("$.blind").doesNotExist());
        detail(post, ownerCookies).andExpect(jsonPath("$.viewer.isOwner").value(true));
    }

    @Test
    void editedPostHasUpdatedAt() throws Exception {
        Post post = testPosts.published(blog, Visibility.PUBLIC);

        mockMvc.perform(put("/api/posts/" + post.getId())
                        .header(HttpHeaders.HOST, TestBlogs.host(blog))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"고침\",\"contentHtml\":\"<p>새 본문</p>\",\"visibility\":\"PUBLIC\","
                                + "\"status\":\"PUBLISHED\"}")
                        .cookie(ownerCookies))
                .andExpect(status().isOk());

        detail(post, null).andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.title").value("고침"));
    }

    @Test
    void hiddenPostsAre404RegardlessOfLogin() throws Exception {
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);
        Post draft = testPosts.draft(blog);
        Post deleted = testPosts.published(blog, Visibility.PUBLIC);
        testPosts.delete(deleted);
        Cookie[] other = testMembers.loginCookies(testMembers.create());

        detail(privatePost, null).andExpect(status().isNotFound());
        detail(privatePost, other).andExpect(status().isNotFound());
        detail(draft, other).andExpect(status().isNotFound());
        detail(deleted, ownerCookies).andExpect(status().isNotFound());
        // 주인은 공개 범위와 상관없이 본다
        detail(privatePost, ownerCookies).andExpect(status().isOk());
    }

    @Test
    void blindedPostShowsReasonOnlyToOwner() throws Exception {
        Post post = testPosts.published(blog, Visibility.PUBLIC);
        testPosts.blind(post);
        moderationLogRepository.save(ModerationLog.record(testMembers.admin(), ModerationAction.BLIND,
                ModerationTargetType.POST, post.getId(), SanctionReason.SPAM, null));

        detail(post, null).andExpect(status().isNotFound());
        detail(post, ownerCookies)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blind.reason").value("SPAM"));
    }

    @Test
    void subscribersOnlyPostShowsNoticeWithoutContent() throws Exception {
        Post post = testPosts.published(blog, Visibility.SUBSCRIBERS);

        detail(post, testMembers.loginCookies(testMembers.create()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIBERS_ONLY"))
                .andExpect(jsonPath("$.detail.blogName").value(blog.getName()))
                .andExpect(jsonPath("$.detail.blogAddress").value(blog.getAddress()))
                .andExpect(jsonPath("$.title").doesNotExist());
    }

    @Test
    void postOfAnotherBlogIs404OnThisAddress() throws Exception {
        Post elsewhere = testPosts.published(testBlogs.create(testMembers.create()), Visibility.PUBLIC);

        detail(elsewhere, null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/999999999").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isNotFound());
    }

    @Test
    void prevAndNextSkipPostsTheViewerCannotSee() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 9, 0);
        Post first = testPosts.published(blog, null, Visibility.PUBLIC, base);
        Post hidden = testPosts.published(blog, null, Visibility.PRIVATE, base.plusDays(1));
        Post third = testPosts.published(blog, null, Visibility.PUBLIC, base.plusDays(2));
        // 다른 블로그 글은 이웃이 아니다
        testPosts.published(testBlogs.create(testMembers.create()), null, Visibility.PUBLIC, base.plusHours(36));

        detail(third, null)
                .andExpect(jsonPath("$.prev.id").value(first.getId()))
                .andExpect(jsonPath("$.next").doesNotExist());
        detail(first, null)
                .andExpect(jsonPath("$.prev").doesNotExist())
                .andExpect(jsonPath("$.next.id").value(third.getId()))
                .andExpect(jsonPath("$.next.title").value("제목"));
        // 주인에게는 비공개 글도 이웃이다
        detail(first, ownerCookies).andExpect(jsonPath("$.next.id").value(hidden.getId()));
    }

    @Test
    void samePublishTimeIsOrderedById() throws Exception {
        LocalDateTime same = LocalDateTime.of(2026, 10, 1, 9, 0);
        Post older = testPosts.published(blog, null, Visibility.PUBLIC, same);
        Post newer = testPosts.published(blog, null, Visibility.PUBLIC, same);

        detail(older, null).andExpect(jsonPath("$.next.id").value(newer.getId()));
        detail(newer, null).andExpect(jsonPath("$.prev.id").value(older.getId()));
    }

    private ResultActions detail(Post post, Cookie[] cookies) throws Exception {
        var request = get("/api/posts/" + post.getId()).header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
