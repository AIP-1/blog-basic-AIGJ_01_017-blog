package com.nhnacademy.blog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
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
 * 블로그 꾸미기·사이드바 모듈·이사·삭제 (스텝 19: T105, T086, T106, T107, spec US10 1~7).
 */
class BlogDecorateMoveDeleteIntegrationTest extends IntegrationTestSupport {

    private static final String ALL_MODULES = """
            [{"moduleType":"PROFILE","isVisible":true},{"moduleType":"SUBSCRIBE","isVisible":true},
             {"moduleType":"POPULAR_POST","isVisible":true},{"moduleType":"VISITOR","isVisible":true},
             {"moduleType":"CATEGORY","isVisible":true},{"moduleType":"TAG","isVisible":false},
             {"moduleType":"RECENT_POST","isVisible":true},{"moduleType":"RECENT_COMMENT","isVisible":false}]""";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member reader;
    Blog blog;
    Cookie[] ownerCookies;
    Cookie[] readerCookies;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        reader = testMembers.create();
        blog = testBlogs.createPrimary(owner);
        ownerCookies = testMembers.loginCookies(owner);
        readerCookies = testMembers.loginCookies(reader);
    }

    // ---------- T105 꾸미기 ----------

    @Test
    void ownerPicksSkinLayoutAndAccentColor() throws Exception {
        send(patch("/api/blog"), blog, ownerCookies,
                "{\"skin\":\"MAGAZINE\",\"listLayout\":\"THUMBNAIL\",\"accentColor\":\"GREEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skin").value("MAGAZINE"))
                .andExpect(jsonPath("$.listLayout").value("THUMBNAIL"))
                .andExpect(jsonPath("$.accentColor").value("GREEN"))
                .andExpect(jsonPath("$.name").value(blog.getName()));
        send(get("/api/blog"), blog, null, null).andExpect(jsonPath("$.skin").value("MAGAZINE"));

        send(patch("/api/blog"), blog, ownerCookies, "{\"accentColor\":\"RED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("accentColor"));
        send(patch("/api/blog"), blog, ownerCookies, "{\"skin\":\"DARK\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("skin"));
        send(patch("/api/blog"), blog, readerCookies, "{\"skin\":\"NOTE\"}").andExpect(status().isForbidden());
    }

    // ---------- T086 사이드바 모듈 ----------

    @Test
    void newBlogStartsWithEightModulesAndThreeHidden() throws Exception {
        Member member = testMembers.create();
        String address = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/blogs").header(HttpHeaders.HOST, "blog.test")
                        .header("X-Requested-With", "XMLHttpRequest").cookie(testMembers.loginCookies(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"" + address + "\",\"name\":\"새 블로그\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/blog/sidebar/modules").header(HttpHeaders.HOST, address + ".blog.test")
                        .cookie(testMembers.loginCookies(member)))
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].moduleType", contains("PROFILE", "CATEGORY", "TAG", "RECENT_POST",
                        "RECENT_COMMENT", "VISITOR", "POPULAR_POST", "SUBSCRIBE")))
                .andExpect(jsonPath("$[*].isVisible", contains(true, true, true, true, true, false, false, false)));
        mockMvc.perform(get("/api/blog/sidebar").header(HttpHeaders.HOST, address + ".blog.test"))
                .andExpect(jsonPath("$.modules[*].type", contains("PROFILE", "CATEGORY", "TAG", "RECENT_POST",
                        "RECENT_COMMENT")));
    }

    @Test
    void sidebarFollowsOwnerOrderAndVisibility() throws Exception {
        long popular = publish(blog, "많이 본 글");
        publish(blog, "덜 본 글");
        jdbcTemplate.update("UPDATE post SET view_count = 50 WHERE id = ?", popular);
        jdbcTemplate.update("INSERT INTO subscription (member_id, blog_id) VALUES (?, ?)", reader.getId(), blog.getId());

        send(put("/api/blog/sidebar/modules"), blog, readerCookies, ALL_MODULES).andExpect(status().isForbidden());
        send(put("/api/blog/sidebar/modules"), blog, ownerCookies, ALL_MODULES).andExpect(status().isNoContent());

        send(get("/api/blog/sidebar"), blog, readerCookies, null)
                .andExpect(jsonPath("$.modules[*].type", contains("PROFILE", "SUBSCRIBE", "POPULAR_POST", "VISITOR",
                        "CATEGORY", "RECENT_POST")))
                .andExpect(jsonPath("$.modules[1].data.subscriberCount").value(1))
                .andExpect(jsonPath("$.modules[1].data.subscribed").value(true))
                .andExpect(jsonPath("$.modules[2].data[0].title").value("많이 본 글"))
                .andExpect(jsonPath("$.modules[2].data[0].viewCount").value(50))
                .andExpect(jsonPath("$.modules[3].data.today").value(0))
                .andExpect(jsonPath("$.modules[3].data.total").value(0));
        send(get("/api/blog/sidebar/modules"), blog, ownerCookies, null)
                .andExpect(jsonPath("$[1].moduleType").value("SUBSCRIBE"))
                .andExpect(jsonPath("$[5].isVisible").value(false));
    }

    @Test
    void sidebarModulesMustBeAllEightAndProfileVisible() throws Exception {
        send(put("/api/blog/sidebar/modules"), blog, ownerCookies,
                ALL_MODULES.replace("{\"moduleType\":\"PROFILE\",\"isVisible\":true}",
                        "{\"moduleType\":\"PROFILE\",\"isVisible\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("modules"));
        send(put("/api/blog/sidebar/modules"), blog, ownerCookies,
                ALL_MODULES.replace("\"TAG\"", "\"CATEGORY\"")).andExpect(status().isBadRequest());
        send(put("/api/blog/sidebar/modules"), blog, ownerCookies, "[{\"moduleType\":\"PROFILE\",\"isVisible\":true}]")
                .andExpect(status().isBadRequest());
        send(put("/api/blog/sidebar/modules"), blog, ownerCookies, ALL_MODULES.replace("\"TAG\"", "\"WEATHER\""))
                .andExpect(status().isBadRequest());
    }

    // ---------- T106 이사 ----------

    @Test
    void movedPostKeepsNumberGoesUncategorizedAndRelinksTagsByName() throws Exception {
        Blog target = testBlogs.create(owner);
        long category = ((Number) JsonPath.read(send(post("/api/categories"), blog, ownerCookies,
                "{\"name\":\"여행\"}").andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long moved = publish(blog, "옮길 글", category, "[\"Spring\",\"여행\"]");
        long stays = publish(blog, "남을 글", null, "[\"여행\"]");
        publish(target, "대상의 글", null, "[\"spring\"]");

        send(post("/api/blog/move-posts"), blog, ownerCookies,
                "{\"postIds\":[" + moved + "],\"targetBlogId\":" + blog.getId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MOVE_TARGET"));
        Blog others = testBlogs.create(reader);
        send(post("/api/blog/move-posts"), blog, ownerCookies,
                "{\"postIds\":[" + moved + "],\"targetBlogId\":" + others.getId() + "}")
                .andExpect(jsonPath("$.code").value("INVALID_MOVE_TARGET"));

        send(post("/api/blog/move-posts"), blog, ownerCookies,
                "{\"postIds\":[" + moved + ", 999999999],\"targetBlogId\":" + target.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedCount").value(1));

        // 옛 주소는 새 블로그의 같은 번호로 301, 카테고리는 미분류, 태그는 대상 블로그의 같은 이름(대소문자 무시)에
        mockMvc.perform(get("/" + moved).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://" + TestBlogs.host(target) + "/" + moved));
        send(get("/api/posts/" + moved), target, null, null)
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.tags", contains("spring", "여행")))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
        send(get("/api/posts/" + stays), blog, null, null).andExpect(status().isOk());
        // 옛 블로그의 "Spring" 태그는 글이 없어져 지워지고, "여행"은 남은 글이 있어 남는다
        assertThat(jdbcTemplate.queryForList("SELECT name FROM tag WHERE blog_id = ? ORDER BY name", String.class,
                blog.getId())).containsExactly("여행");
    }

    @Test
    void moveTargetFollowsChainAndRejectsCycle() throws Exception {
        Blog b = testBlogs.create(owner);
        Blog c = testBlogs.create(owner);
        send(put("/api/blog/moved-to"), blog, ownerCookies, "{\"targetBlogId\":" + b.getId() + "}")
                .andExpect(status().isNoContent());
        // B를 C로: A도 한 번에 C로
        send(put("/api/blog/moved-to"), b, ownerCookies, "{\"targetBlogId\":" + c.getId() + "}")
                .andExpect(status().isNoContent());
        assertThat(movedTo(blog)).isEqualTo(c.getId());
        mockMvc.perform(get("/").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://" + TestBlogs.host(c) + "/"));
        // C를 A로: A의 최종이 C라 순환
        send(put("/api/blog/moved-to"), c, ownerCookies, "{\"targetBlogId\":" + blog.getId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MOVE_TARGET"));
        // 주인은 옛 블로그 관리 API를 계속 쓴다
        send(delete("/api/blog/moved-to"), blog, ownerCookies, null).andExpect(status().isNoContent());
        assertThat(movedTo(blog)).isNull();

        // 이사한 B를 지워도 B 주소는 새 블로그 C로 301(삭제 여부 무관). C를 지우면 이어지지 않는다
        send(delete("/api/blog"), b, ownerCookies, "{\"confirmAddress\":\"" + b.getAddress() + "\"}")
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/").header(HttpHeaders.HOST, TestBlogs.host(b)))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://" + TestBlogs.host(c) + "/"));
        send(delete("/api/blog"), c, ownerCookies, "{\"confirmAddress\":\"" + c.getAddress() + "\"}")
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/").header(HttpHeaders.HOST, TestBlogs.host(b))).andExpect(status().isNotFound());
    }

    // ---------- T107 삭제 ----------

    @Test
    void deletingBlogDeletesRemainingPostsKeepsAddressAndMoveLink() throws Exception {
        Blog side = testBlogs.create(owner);
        long post = publish(side, "함께 지워질 글");
        long moved = publish(side, "옮긴 글");
        send(post("/api/blog/move-posts"), side, ownerCookies,
                "{\"postIds\":[" + moved + "],\"targetBlogId\":" + blog.getId() + "}").andExpect(status().isOk());
        send(post("/api/posts/" + post + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()), side,
                readerCookies, "{\"content\":\"댓글\"}").andExpect(status().isCreated());

        send(get("/api/blog/deletion-preview"), side, ownerCookies, null)
                .andExpect(jsonPath("$.remainingPostCount").value(1))
                .andExpect(jsonPath("$.isPrimary").value(false));
        send(delete("/api/blog"), side, ownerCookies, "{\"confirmAddress\":\"wrong\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("confirmAddress"));
        send(delete("/api/blog"), blog, ownerCookies, "{\"confirmAddress\":\"" + blog.getAddress() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIMARY_BLOG"));

        send(delete("/api/blog"), side, ownerCookies, "{\"confirmAddress\":\"" + side.getAddress() + "\"}")
                .andExpect(status().isNoContent());

        send(get("/api/blog"), side, ownerCookies, null).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM post WHERE id = ?", Boolean.class,
                post)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment WHERE post_id = ? AND deleted_at IS NULL",
                Integer.class, post)).isZero();
        // 옮긴 글은 새 블로그에 남고, 지운 블로그의 옛 주소도 여전히 301. 옮기지 않은 글은 404
        mockMvc.perform(get("/" + moved).header(HttpHeaders.HOST, TestBlogs.host(side)))
                .andExpect(status().isMovedPermanently());
        mockMvc.perform(get("/" + post).header(HttpHeaders.HOST, TestBlogs.host(side)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/").header(HttpHeaders.HOST, TestBlogs.host(side))).andExpect(status().isNotFound());
        // 주소는 다시 쓸 수 없다
        mockMvc.perform(get("/api/blogs/address-availability").param("address", side.getAddress())
                        .header(HttpHeaders.HOST, "blog.test").cookie(ownerCookies))
                .andExpect(jsonPath("$.available").value(false));
    }

    // ---------- 도우미 ----------

    private Long movedTo(Blog blog) {
        return jdbcTemplate.queryForObject("SELECT moved_to_blog_id FROM blog WHERE id = ?", Long.class, blog.getId());
    }

    private long publish(Blog target, String title) throws Exception {
        return publish(target, title, null, "[]");
    }

    private long publish(Blog target, String title, Long categoryId, String tagNames) throws Exception {
        String body = """
                {"title":"%s","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"PUBLISHED",
                 "categoryId":%s,"tagNames":%s}""".formatted(title, categoryId == null ? "null" : categoryId, tagNames);
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), target,
                ownerCookies, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Blog target, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(target)).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
