package com.nhnacademy.blog.search;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostBody;
import com.nhnacademy.blog.post.domain.PostRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 전체 검색 (T065, SRCH-02, 스텝 15 "볼 수 없는 글(비공개, 구독자 공개, 숨김, 제한 블로그)은 결과에 나오지 않는다").
 * 다른 테스트가 남긴 글과 섞이지 않게 테스트마다 아무도 쓰지 않을 검색어(word)를 쓴다.
 */
class GlobalSearchIntegrationTest extends IntegrationTestSupport {

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
    JdbcTemplate jdbcTemplate;

    String word;
    Member alice;
    Member bob;
    Blog aliceBlog;
    Blog bobBlog;

    @BeforeEach
    void setUp() {
        word = "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        alice = testMembers.create();
        bob = testMembers.create();
        aliceBlog = testBlogs.createPrimary(alice);
        bobBlog = testBlogs.createPrimary(bob);
    }

    @Test
    void findsPostsOfEveryBlogByTitleTextOrTagNewestFirst() throws Exception {
        long older = post(aliceBlog, "앨리스의 " + word + " 글", "본문", Visibility.PUBLIC, 2).getId();
        long newer = post(bobBlog, "밥의 글", "본문에 " + word.toUpperCase() + " 있음", Visibility.PUBLIC, 1).getId();

        search(null, word, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(newer))
                .andExpect(jsonPath("$.content[0].blog.address").value(bobBlog.getAddress()))
                .andExpect(jsonPath("$.content[1].id").value(older))
                .andExpect(jsonPath("$.totalElements").value(2));
        // type=post는 기본값과 같다
        search(null, word, "post").andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void postsNobodyElseMaySeeAreNotFound() throws Exception {
        long open = post(aliceBlog, word + " 공개", "본문", Visibility.PUBLIC, 1).getId();
        post(aliceBlog, word + " 비공개", "본문", Visibility.PRIVATE, 1);
        post(aliceBlog, word + " 구독자", "본문", Visibility.SUBSCRIBERS, 1);
        testPosts.blind(post(aliceBlog, word + " 숨김", "본문", Visibility.PUBLIC, 1));
        testPosts.delete(post(aliceBlog, word + " 지움", "본문", Visibility.PUBLIC, 1));
        Blog restricted = testBlogs.create(bob);
        post(restricted, word + " 제한 블로그", "본문", Visibility.PUBLIC, 1);
        testBlogs.restrict(restricted);
        Member suspended = testMembers.create();
        Blog suspendedBlog = testBlogs.create(suspended);
        post(suspendedBlog, word + " 정지 회원", "본문", Visibility.PUBLIC, 1);
        testBlogs.suspendOwner(suspendedBlog);

        search(null, word, null).andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) open)));
        // 주인이어도 전체 검색에는 자기 비공개 글이 없다(블로그 안 검색에서 찾는다)
        search(testMembers.loginCookies(alice), word, null)
                .andExpect(jsonPath("$.content", hasSize(1)));
        // 구독한 회원에게는 구독자 공개 글도 나온다
        jdbcTemplate.update("INSERT INTO subscription (member_id, blog_id) VALUES (?, ?)", bob.getId(),
                aliceBlog.getId());
        search(testMembers.loginCookies(bob), word, null).andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void findsBlogsByNameOrDescriptionWithOwnerAndSubscriberCount() throws Exception {
        rename(aliceBlog, word + " 일기", null);
        rename(bobBlog, "밥의 블로그", "소개에 " + word);
        jdbcTemplate.update("INSERT INTO subscription (member_id, blog_id) VALUES (?, ?)", bob.getId(),
                aliceBlog.getId());
        Blog restricted = testBlogs.create(bob);
        rename(restricted, word + " 제한", null);
        testBlogs.restrict(restricted);
        Blog deleted = testBlogs.create(alice);
        rename(deleted, word + " 지운 블로그", null);
        testBlogs.delete(deleted);

        search(null, word, "blog")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].blog.address",
                        containsInAnyOrder(aliceBlog.getAddress(), bobBlog.getAddress())))
                .andExpect(jsonPath("$.content[?(@.blog.address == '" + aliceBlog.getAddress()
                        + "')].subscriberCount").value(1))
                .andExpect(jsonPath("$.content[?(@.blog.address == '" + aliceBlog.getAddress()
                        + "')].owner.nickname").value(alice.getNickname()))
                .andExpect(jsonPath("$.content[?(@.blog.address == '" + bobBlog.getAddress()
                        + "')].owner.primaryBlogAddress").value(bobBlog.getAddress()));
    }

    @Test
    void badInputIs400AndBlogAddressStillSearchesOnlyThatBlog() throws Exception {
        search(null, "  ", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        search(null, word, "user").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));

        post(aliceBlog, word + " 앨리스", "본문", Visibility.PUBLIC, 1);
        post(bobBlog, word + " 밥", "본문", Visibility.PUBLIC, 1);
        mockMvc.perform(get("/api/search").param("q", word).header(HttpHeaders.HOST, TestBlogs.host(aliceBlog)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].blog.address").value(aliceBlog.getAddress()));
    }

    private Post post(Blog blog, String title, String text, Visibility visibility, int daysAgo) {
        return postRepository.save(Post.published(blog, null, title, new PostBody("<p>" + text + "</p>", text, text),
                visibility, null, LocalDateTime.now().minusDays(daysAgo)));
    }

    private void rename(Blog blog, String name, String description) {
        jdbcTemplate.update("UPDATE blog SET name = ?, description = ? WHERE id = ?", name, description, blog.getId());
    }

    private ResultActions search(Cookie[] cookies, String q, String type) throws Exception {
        var request = get("/api/search").param("q", q).header(HttpHeaders.HOST, "blog.test");
        if (type != null) {
            request.param("type", type);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
