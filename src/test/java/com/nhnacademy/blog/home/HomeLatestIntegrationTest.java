package com.nhnacademy.blog.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 홈 최신 글 (T042, HOME-01, spec US3 시나리오 1·2). 다른 테스트의 글과 섞이지 않게 먼 미래 시각에 글을 둔다.
 */
class HomeLatestIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    @Test
    void onlyVisiblePostsLatestFirstTwentyAtATimeWithoutDuplicatesOrGaps() throws Exception {
        // 다른 테스트가 만든 글보다 늘 위에 오도록 미래 시각을 쓴다
        LocalDateTime base = LocalDateTime.of(2099, 1, 1, 0, 0).plusDays(System.nanoTime() % 1000);
        Blog alpha = testBlogs.create(testMembers.create());
        Blog beta = testBlogs.create(testMembers.create());
        List<Long> visible = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Blog blog = i % 2 == 0 ? alpha : beta;
            visible.add(0, testPosts.published(blog, null, Visibility.PUBLIC, base.plusMinutes(i)).getId());
        }
        Post privatePost = testPosts.published(alpha, null, Visibility.PRIVATE, base.plusMinutes(30));
        Post blinded = testPosts.published(beta, null, Visibility.PUBLIC, base.plusMinutes(31));
        testPosts.blind(blinded);
        Blog restricted = testBlogs.create(testMembers.create());
        testPosts.published(restricted, null, Visibility.PUBLIC, base.plusMinutes(32));
        testBlogs.restrict(restricted);

        String first = mockMvc.perform(get("/api/home/latest").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].blog.address").isString())
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        List<Number> firstIds = JsonPath.read(first, "$.content[*].id");
        assertThat(firstIds.stream().map(Number::longValue).toList()).isEqualTo(visible.subList(0, 20))
                .doesNotContain(privatePost.getId(), blinded.getId());

        // 더보기 사이에 새 글이 올라와도 다음 묶음은 이어서 나온다
        testPosts.published(alpha, null, Visibility.PUBLIC, base.plusMinutes(40));
        String cursor = JsonPath.read(first, "$.nextCursor");
        String second = mockMvc.perform(get("/api/home/latest").param("cursor", cursor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> secondIds = JsonPath.read(second, "$.content[*].id");
        assertThat(secondIds.stream().map(Number::longValue).limit(5).toList()).isEqualTo(visible.subList(20, 25));
    }

    @Test
    void lastPageHasNoNextCursor() throws Exception {
        Blog blog = testBlogs.create(testMembers.create());
        Post oldest = testPosts.published(blog, null, Visibility.PUBLIC, LocalDateTime.of(1990, 1, 1, 0, 0));
        String cursor = new TimeIdCursor(oldest.getPublishedAt(), oldest.getId() + 1)
                .encode();

        mockMvc.perform(get("/api/home/latest").param("cursor", cursor))
                .andExpect(jsonPath("$.content[0].id").value(oldest.getId()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mockMvc.perform(get("/api/home/latest").param("cursor", "%%망가진"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cursor"));
    }

}
