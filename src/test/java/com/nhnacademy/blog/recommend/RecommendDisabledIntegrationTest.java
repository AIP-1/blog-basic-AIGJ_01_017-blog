package com.nhnacademy.blog.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.recommend.application.PostEmbeddingService;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 추천을 끈 1차 배포(app.recommend.enabled=false). 추천 DB·임베딩 부품 없이 앱이 뜨고,
 * 비슷한 글은 빈 목록이며, 볼 수 없는 원본 글은 켜 둘 때와 같이 404다.
 */
@TestPropertySource(properties = "app.recommend.enabled=false")
class RecommendDisabledIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext context;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Test
    void startsWithoutRecommendPartsAndGivesEmptyList() throws Exception {
        assertThat(context.getBeanNamesForType(PostEmbeddingRepository.class)).isEmpty();
        assertThat(context.getBeanNamesForType(PostEmbeddingService.class)).isEmpty();

        Member member = testMembers.create();
        Blog blog = testBlogs.create(member);
        Cookie[] owner = testMembers.loginCookies(member);
        long publicPost = publish(blog, owner, "PUBLIC");
        long privatePost = publish(blog, owner, "PRIVATE");

        mockMvc.perform(get("/api/posts/" + publicPost + "/similar").header(TestBlogs.HEADER, blog.getAddress()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/posts/" + privatePost + "/similar").header(TestBlogs.HEADER, blog.getAddress()))
                .andExpect(status().isNotFound());
    }

    @Test
    void semanticSearchIsUnavailableButKeywordSearchWorks() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "추천 끔").param("mode", "semantic"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEMANTIC_SEARCH_UNAVAILABLE"));
        mockMvc.perform(get("/api/search").param("q", "추천 끔"))
                .andExpect(status().isOk());
    }

    private long publish(Blog blog, Cookie[] cookies, String visibility) throws Exception {
        String response = mockMvc.perform(post("/api/posts")
                        .header(TestBlogs.HEADER, blog.getAddress())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"추천 끔","contentHtml":"<p>추천 끔</p>","visibility":"%s","status":"PUBLISHED"}
                                """.formatted(visibility))
                        .cookie(cookies))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

}
