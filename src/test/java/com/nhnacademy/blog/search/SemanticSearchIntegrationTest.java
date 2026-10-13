package com.nhnacademy.blog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.TestRecommendConfiguration;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 의미 검색 (T120, T121, OWN-07). 임베딩은 가짜(낱말 주머니, TestRecommendConfiguration)라 같은 낱말이 많이 겹칠수록 가깝다.
 * 낱말은 테스트마다 다른 표식(mark)으로 만든다. 가짜 임베딩은 낱말을 해시로 1024칸 중 하나에 넣으므로, 이 테스트의 낱말끼리
 * 칸이 겹치지 않는 표식을 고른다(distinctMark). 다른 테스트의 글 낱말과는 칸이 겹칠 수 있어, 전체 검색은 이 테스트의 글만 골라 본다.
 * <p>
 * 글 본문은 제목과 같다(publish). 넣는 글자가 "제목\n본문"이라 제목 낱말이 두 번 들어간다.
 * 검색어 "A B"와의 코사인 유사도: 제목 "A B" 글 = 1.00, 제목 "A B C" 글 = 0.82, 제목 "C" 글 = 0(기준 0.40 미만이라 빠짐).
 */
class SemanticSearchIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    NamedParameterJdbcTemplate embeddingJdbc;

    Blog blog;
    Cookie[] owner;
    Blog otherBlog;
    Cookie[] otherOwner;
    String a;
    String b;
    String c;

    @BeforeEach
    void setUp() {
        Member member = testMembers.create();
        blog = testBlogs.create(member);
        owner = testMembers.loginCookies(member);
        Member other = testMembers.create();
        otherBlog = testBlogs.create(other);
        otherOwner = testMembers.loginCookies(other);
        String mark = distinctMark();
        a = mark + "a";
        b = mark + "b";
        c = mark + "c";
    }

    /** a, b, c, 태그 낱말(c + "tag", c + "new")과 "태그"가 가짜 임베딩에서 모두 다른 칸에 들어가는 표식. */
    private static String distinctMark() {
        while (true) {
            String mark = "m" + UUID.randomUUID().toString().substring(0, 8);
            List<String> words = List.of(mark + "a", mark + "b", mark + "c", mark + "ctag", mark + "cnew", "태그");
            long buckets = words.stream()
                    .map(word -> Math.floorMod(word.toLowerCase(Locale.ROOT).hashCode(), TestRecommendConfiguration.DIMENSIONS))
                    .distinct().count();
            if (buckets == words.size()) {
                return mark;
            }
        }
    }

    @Test
    void searchAllGivesVisibleCloseOnesInOrderWithSimilarity() throws Exception {
        long exact = publish(blog, owner, a + " " + b, "PUBLIC", null);
        long close = publish(otherBlog, otherOwner, a + " " + b + " " + c, "PUBLIC", null);
        long far = publish(blog, owner, c, "PUBLIC", null);
        long hidden = publish(blog, owner, a + " " + b, "PRIVATE", null);
        awaitEmbeddings(exact, close, far, hidden);

        String body = search(null, null, a + " " + b, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 너무 먼 글은 빠지고, 비공개 글은 가장 가까워도 전체 검색에 나오지 않는다(주인이 봐도)
        List<Long> ours = List.of(exact, close, far, hidden);
        assertThat(ids(body).stream().filter(ours::contains)).containsExactly(exact, close);
        assertThat(of(body, exact, "similarity")).isEqualTo(1.0);
        assertThat(of(body, close, "similarity")).isEqualTo(0.82);
        assertThat(of(body, close, "post.blog.address")).isEqualTo(otherBlog.getAddress());

        String asOwner = search(null, owner, a + " " + b, null).andReturn().getResponse().getContentAsString();
        assertThat(ids(asOwner)).doesNotContain(hidden);
    }

    @Test
    void pagesAreCutInSimilarityOrder() throws Exception {
        long exact = publish(blog, owner, a + " " + b, "PUBLIC", null);
        long close = publish(blog, owner, a + " " + b + " " + c, "PUBLIC", null);
        awaitEmbeddings(exact, close);

        mockMvc.perform(get("/api/search").header(TestBlogs.HEADER, blog.getAddress())
                        .param("q", a + " " + b).param("mode", "semantic").param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].post.id").value(close))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void blogSearchStaysInTheBlogAndShowsOwnPrivatePostsToTheOwner() throws Exception {
        long mine = publish(blog, owner, a + " " + b, "PUBLIC", null);
        long minePrivate = publish(blog, owner, a + " " + b + " " + c, "PRIVATE", null);
        long othersClose = publish(otherBlog, otherOwner, a + " " + b, "PUBLIC", null);
        awaitEmbeddings(mine, minePrivate, othersClose);

        String asOwner = search(blog, owner, a + " " + b, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(asOwner)).containsExactly(mine, minePrivate);

        String asGuest = search(blog, null, a + " " + b, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(asGuest)).containsExactly(mine);
    }

    @Test
    void tagNamesAreEmbeddedAndRenamingATagRemakesTheEmbedding() throws Exception {
        String tag = c + "tag";
        long id = publish(blog, owner, a, "PUBLIC", tag);
        awaitEmbeddings(id);
        assertThat(textVersion(id)).isEqualTo(2);

        // 넣는 글자 "A\n태그: T\nA": 검색어 "A T"와 0.87(태그가 들어 있다), "A N"과 0.58
        String renamed = c + "new";
        assertThat(firstSimilarity(a + " " + tag)).isEqualTo(0.87);
        String before = embedding(id);

        long tagId = tagId(tag);
        mockMvc.perform(patch("/api/tags/" + tagId).header(TestBlogs.HEADER, blog.getAddress())
                        .header("X-Requested-With", "XMLHttpRequest").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + renamed + "\"}"))
                .andExpect(status().isNoContent());
        await(() -> !before.equals(embedding(id)));

        assertThat(firstSimilarity(a + " " + renamed)).isEqualTo(0.87);
    }

    @Test
    void wrongModeOrBlogTypeIsRejected() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "x").param("mode", "vector"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("mode"));
        mockMvc.perform(get("/api/search").param("q", "x").param("mode", "semantic").param("type", "blog"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("mode"));
        mockMvc.perform(get("/api/search").param("q", " ").param("mode", "semantic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
    }

    private long publish(Blog target, Cookie[] cookies, String title, String visibility, String tag) throws Exception {
        String tags = tag == null ? "[]" : "[\"" + tag + "\"]";
        String response = mockMvc.perform(post("/api/posts")
                        .header(TestBlogs.HEADER, target.getAddress())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","contentHtml":"<p>%s</p>","visibility":"%s","status":"PUBLISHED","tagNames":%s}
                                """.formatted(title, title, visibility, tags))
                        .cookie(cookies))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** blog가 있으면 블로그 안 검색, 없으면 전체 검색. */
    private ResultActions search(Blog target, Cookie[] cookies, String q, String size) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/search").param("q", q).param("mode", "semantic");
        if (target != null) {
            request.header(TestBlogs.HEADER, target.getAddress());
        }
        if (cookies != null) {
            request.cookie(cookies);
        }
        if (size != null) {
            request.param("size", size);
        }
        return mockMvc.perform(request);
    }

    private double firstSimilarity(String q) throws Exception {
        String body = search(blog, owner, q, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.content[0].similarity");
    }

    private long tagId(String name) throws Exception {
        String body = mockMvc.perform(get("/api/manage/tags").header(TestBlogs.HEADER, blog.getAddress()).cookie(owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    private void awaitEmbeddings(long... postIds) throws InterruptedException {
        for (long id : postIds) {
            await(() -> embedding(id) != null);
        }
    }

    private String embedding(long postId) {
        return embeddingJdbc.query("SELECT embedding::text FROM post_embedding WHERE post_id = :id",
                Map.of("id", postId), rs -> rs.next() ? rs.getString(1) : null);
    }

    private Integer textVersion(long postId) {
        return embeddingJdbc.queryForObject("SELECT text_version FROM post_embedding WHERE post_id = :id",
                Map.of("id", postId), Integer.class);
    }

    /** 결과에서 이 글 줄의 값 하나. */
    private static Object of(String body, long postId, String path) {
        List<Object> values = JsonPath.read(body, "$.content[?(@.post.id == " + postId + ")]." + path);
        return values.getFirst();
    }

    private static List<Long> ids(String body) {
        List<Number> ids = JsonPath.read(body, "$.content[*].post.id");
        return ids.stream().map(Number::longValue).toList();
    }

    /** 임베딩은 커밋된 뒤 비동기로 만들어지므로 5초까지 기다린다. */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("5초 안에 조건이 맞지 않았습니다");
            }
            Thread.sleep(50);
        }
    }

}
