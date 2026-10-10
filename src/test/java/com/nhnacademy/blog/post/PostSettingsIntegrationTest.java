package com.nhnacademy.blog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.application.ScheduledPublisher;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * 글·댓글·카테고리·태그 설정 (스텝 17: T060a CAT-04, T097 CAT-05, T098 TAG-04, T101 CMT-06, T102 CMT-07,
 * T103 POST-13, T104 OWN-05, spec US11 시나리오 2~6).
 */
class PostSettingsIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    ScheduledPublisher scheduledPublisher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member reader;
    Member stranger;
    Blog blog;
    Cookie[] ownerCookies;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        reader = testMembers.create();
        stranger = testMembers.create();
        blog = testBlogs.create(owner);
        ownerCookies = testMembers.loginCookies(owner);
    }

    // ---------- CAT-05 비공개 카테고리 ----------

    @Test
    void postsInPrivateCategoryOrItsChildAreHiddenFromOthers() throws Exception {
        long dev = category("개발", null);
        long spring = category("Spring", dev);
        long open = publish("공개 글", null, "PUBLIC");
        long inDev = publish("개발 글", dev, "PUBLIC");
        long inSpring = publish("스프링 글", spring, "PUBLIC");

        send(patch("/api/categories/" + dev), ownerCookies, "{\"isPrivate\":true}").andExpect(status().isNoContent());

        // 남에게는 그 카테고리와 하위의 글이 없는 글이다: 목록·글 수·글 상세·카테고리 트리·전체 검색
        mockMvc.perform(get("/api/posts").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.content[*].id", contains((int) open)));
        mockMvc.perform(get("/api/blog").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.postCount").value(1));
        for (long hidden : new long[] {inDev, inSpring}) {
            mockMvc.perform(get("/api/posts/" + hidden).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/categories").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.categories").isEmpty());
        mockMvc.perform(get("/api/search").param("q", "스프링 글").header(HttpHeaders.HOST, "blog.test"))
                .andExpect(jsonPath("$.content[?(@.id == " + inSpring + ")]").isEmpty());
        // 주인은 그대로 본다
        send(get("/api/posts"), ownerCookies, null).andExpect(jsonPath("$.totalElements").value(3));
        send(get("/api/posts/" + inSpring), ownerCookies, null).andExpect(status().isOk());
        send(get("/api/categories"), ownerCookies, null)
                .andExpect(jsonPath("$.categories[0].isPrivate").value(true));

        send(patch("/api/categories/" + dev), ownerCookies, "{\"isPrivate\":false}");
        mockMvc.perform(get("/api/posts/" + inSpring).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isOk());
    }

    // ---------- CAT-04 순서·상하위 ----------

    @Test
    void reorderMovesCategoriesAndKeepsTwoLevels() throws Exception {
        long a = category("A", null);
        long b = category("B", null);
        long c = category("C", a);

        // C를 최상위 맨 위로, B를 A의 하위로
        send(put("/api/categories/order"), ownerCookies, """
                [{"id":%d,"parentId":null,"sortOrder":0},{"id":%d,"parentId":null,"sortOrder":1},
                 {"id":%d,"parentId":%d,"sortOrder":0}]""".formatted(c, a, b, a))
                .andExpect(status().isNoContent());
        send(get("/api/categories"), ownerCookies, null)
                .andExpect(jsonPath("$.categories[*].name", contains("C", "A")))
                .andExpect(jsonPath("$.categories[1].children[*].name", contains("B")));

        // 하위를 가진 A를 C 아래로(3단계) → 409, 빠진 카테고리 → 400, 비회원 401, 남 403
        send(put("/api/categories/order"), ownerCookies, """
                [{"id":%d,"parentId":null,"sortOrder":0},{"id":%d,"parentId":%d,"sortOrder":0},
                 {"id":%d,"parentId":%d,"sortOrder":0}]""".formatted(c, a, c, b, a))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_DEPTH"));
        send(put("/api/categories/order"), ownerCookies, "[{\"id\":%d,\"parentId\":null,\"sortOrder\":0}]".formatted(a))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("order"));
        send(put("/api/categories/order"), null, "[]").andExpect(status().isUnauthorized());
        send(put("/api/categories/order"), testMembers.loginCookies(stranger), "[]").andExpect(status().isForbidden());
    }

    @Test
    void reorderRejectsSameNameInSamePlace() throws Exception {
        long a = category("A", null);
        long dup = category("같은", null);
        long child = category("같은", a);

        send(put("/api/categories/order"), ownerCookies, """
                [{"id":%d,"parentId":null,"sortOrder":0},{"id":%d,"parentId":null,"sortOrder":1},
                 {"id":%d,"parentId":null,"sortOrder":2}]""".formatted(a, dup, child))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_TAKEN"));
    }

    // ---------- TAG-04 태그 관리 ----------

    @Test
    void renameAndDeleteTagsKeepPosts() throws Exception {
        long post = publishTagged("스프링 글", "[\"spring\",\"jpa\"]");
        long springId = tagId("spring");

        send(patch("/api/tags/" + springId), ownerCookies, "{\"name\":\"JPA\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_TAKEN"));
        send(patch("/api/tags/" + springId), ownerCookies, "{\"name\":\"a/b\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        send(patch("/api/tags/" + springId), testMembers.loginCookies(stranger), "{\"name\":\"x\"}")
                .andExpect(status().isForbidden());
        send(patch("/api/tags/" + springId), ownerCookies, "{\"name\":\" #Spring Boot \"}")
                .andExpect(status().isNoContent());
        send(get("/api/posts/" + post), ownerCookies, null)
                .andExpect(jsonPath("$.tags", contains("Spring Boot", "jpa")));

        send(delete("/api/tags/" + tagId("jpa")), ownerCookies, null).andExpect(status().isNoContent());
        send(get("/api/posts/" + post), ownerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", contains("Spring Boot")));
        send(get("/api/tags"), ownerCookies, null).andExpect(jsonPath("$[*].name", contains("Spring Boot")));
        send(delete("/api/tags/999999"), ownerCookies, null).andExpect(status().isNotFound());
    }

    @Test
    void tagWithNoPostLeftIsRemovedWhenPostsAreDeletedOrUntagged() throws Exception {
        long first = publishTagged("첫 글", "[\"spring\",\"jpa\"]");
        long second = publishTagged("둘째 글", "[\"jpa\"]");
        long springId = tagId("spring");

        // 글을 지우면 그 글에만 있던 태그(spring)가 없어진다. 다른 글에도 단 태그(jpa)는 남는다
        send(delete("/api/posts/" + first), ownerCookies, null).andExpect(status().isNoContent());
        send(get("/api/manage/tags"), ownerCookies, null)
                .andExpect(jsonPath("$[*].name", contains("jpa")))
                .andExpect(jsonPath("$[0].postCount").value(1));
        send(get("/api/posts").param("tag", "spring"), null, null).andExpect(status().isNotFound());
        send(patch("/api/tags/" + springId), ownerCookies, "{\"name\":\"x\"}").andExpect(status().isNotFound());
        // 보이지 않는 옛 태그 때문에 409가 나지 않는다
        send(patch("/api/tags/" + tagId("jpa")), ownerCookies, "{\"name\":\"spring\"}")
                .andExpect(status().isNoContent());

        // 글을 고쳐 태그를 모두 빼도 같다
        save(second, """
                {"title":"둘째 글","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"PUBLISHED","tagNames":[]}""")
                .andExpect(status().isOk());
        send(get("/api/manage/tags"), ownerCookies, null).andExpect(jsonPath("$", hasSize(0)));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag WHERE blog_id = ?", Integer.class,
                blog.getId())).isZero();
    }

    @Test
    void manageTagListIncludesDraftOnlyTagsAndTagPageHidesInvisibleOnes() throws Exception {
        publishTagged("공개 글", "[\"spring\"]");
        send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), ownerCookies, """
                {"title":"초안","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"DRAFT","tagNames":["draft-only"]}""")
                .andExpect(status().isCreated());
        long privatePost = publishTagged("비공개 글", "[\"secret\"]");
        send(patch("/api/posts/" + privatePost + "/visibility"), ownerCookies, "{\"visibility\":\"PRIVATE\"}")
                .andExpect(status().isNoContent());

        // 관리 표: 임시저장 글에만 단 태그도 나오고, 발행 글 수가 따로 있다
        send(get("/api/manage/tags"), ownerCookies, null)
                .andExpect(jsonPath("$[*].name", contains("draft-only", "secret", "spring")))
                .andExpect(jsonPath("$[0].postCount").value(1))
                .andExpect(jsonPath("$[0].publishedCount").value(0))
                .andExpect(jsonPath("$[1].publishedCount").value(1));
        send(get("/api/manage/tags"), testMembers.loginCookies(stranger), null).andExpect(status().isForbidden());
        send(get("/api/manage/tags"), null, null).andExpect(status().isUnauthorized());
        // 블로그 화면용 목록에는 발행 글의 태그만
        send(get("/api/tags"), ownerCookies, null).andExpect(jsonPath("$[*].name", contains("secret", "spring")));

        // 태그 주소: 볼 수 있는 글이 없으면 없는 태그와 같이 404
        send(get("/api/posts").param("tag", "secret"), null, null).andExpect(status().isNotFound());
        send(get("/api/posts").param("tag", "secret"), ownerCookies, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        send(get("/api/posts").param("tag", "draft-only"), ownerCookies, null).andExpect(status().isNotFound());
        send(get("/api/posts").param("tag", "spring"), null, null).andExpect(jsonPath("$.content", hasSize(1)));
    }

    // ---------- CMT-06 비밀댓글, CMT-07 댓글 허용 ----------

    @Test
    void secretCommentIsForPostOwnerAndAuthor() throws Exception {
        long post = publish("글", null, "PUBLIC");
        comment(reader, post, "{\"content\":\"주인만 보세요\",\"secret\":true}").andExpect(status().isCreated());

        for (Member viewer : new Member[] {owner, reader}) {
            send(get("/api/posts/" + post + "/comments"), testMembers.loginCookies(viewer), null)
                    .andExpect(jsonPath("$.content[0].content").value("주인만 보세요"));
        }
        send(get("/api/posts/" + post + "/comments"), testMembers.loginCookies(stranger), null)
                .andExpect(jsonPath("$.content[0].state").value("SECRET"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }

    @Test
    void commentsCanBeClosedPerPost() throws Exception {
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                ownerCookies, """
                        {"title":"댓글 막은 글","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"PUBLISHED",
                         "commentAllowed":false}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long post = ((Number) JsonPath.read(response, "$.id")).longValue();

        send(get("/api/posts/" + post), testMembers.loginCookies(reader), null)
                .andExpect(jsonPath("$.commentAllowed").value(false));
        comment(reader, post, "{\"content\":\"막혔나요\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMENTS_DISABLED"));

        // 다시 열면 쓸 수 있다. 보내지 않으면 그대로다
        save(post, "{\"title\":\"댓글 막은 글\",\"contentHtml\":\"<p>본문</p>\",\"visibility\":\"PUBLIC\","
                + "\"status\":\"PUBLISHED\",\"commentAllowed\":true}").andExpect(status().isOk());
        save(post, "{\"title\":\"제목만 고침\",\"contentHtml\":\"<p>본문</p>\",\"visibility\":\"PUBLIC\","
                + "\"status\":\"PUBLISHED\"}").andExpect(status().isOk());
        comment(reader, post, "{\"content\":\"열렸네요\"}").andExpect(status().isCreated());
    }

    // ---------- POST-13 예약 발행 ----------

    @Test
    void scheduledPostIsHiddenUntilItsTimeThenPublishedAtThatTime() throws Exception {
        LocalDateTime at = LocalDateTime.now().plusHours(1).withNano(0);
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                ownerCookies, """
                        {"title":"예약 글","contentHtml":"<p>본문</p>","visibility":"PRIVATE","status":"SCHEDULED",
                         "scheduledAt":"%s"}""".formatted(at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
        long post = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/api/posts/" + post).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isNotFound());
        send(get("/api/manage/posts/" + post), ownerCookies, null)
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledAt").value(org.hamcrest.Matchers.startsWith(
                        at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))));
        send(get("/api/manage/posts").param("status", "SCHEDULED"), ownerCookies, null)
                .andExpect(jsonPath("$.content", hasSize(1)));

        // 시각 전에는 작업이 돌아도 그대로, 시각이 지나면 발행되고 그 시각이 발행 시각, 공개 범위는 공개
        assertThat(scheduledPublisher.publishDue()).isZero();
        LocalDateTime past = LocalDateTime.now().minusMinutes(3).withNano(0);
        jdbcTemplate.update("UPDATE post SET scheduled_at = ? WHERE id = ?", past, post);
        assertThat(scheduledPublisher.publishDue()).isEqualTo(1);
        mockMvc.perform(get("/api/posts/" + post).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.publishedAt").value(org.hamcrest.Matchers.startsWith(
                        past.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))));

        // 발행한 글은 다시 예약할 수 없다
        save(post, """
                {"title":"예약 글","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"SCHEDULED",
                 "scheduledAt":"%s"}""".formatted(at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    @Test
    void schedulerEntryPointPersistsThePublishing() throws Exception {
        // 스케줄러가 부르는 run()을 거쳐도 발행이 저장되어야 한다. run()이 같은 객체의 publishDue()를 부를 때
        // 트랜잭션이 걸리지 않아 저장되지 않던 버그(확인용 서버에서 매분 "예약 발행 1개"가 반복됨)를 막는다
        LocalDateTime at = LocalDateTime.now().plusHours(1).withNano(0);
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                ownerCookies, """
                        {"title":"예약 글","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"SCHEDULED",
                         "scheduledAt":"%s"}""".formatted(at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long post = ((Number) JsonPath.read(response, "$.id")).longValue();
        jdbcTemplate.update("UPDATE post SET scheduled_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), post);

        scheduledPublisher.run();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM post WHERE id = ?", String.class, post))
                .isEqualTo("PUBLISHED");
    }

    // ---------- OWN-05 같은 카테고리 다른 글 ----------

    @Test
    void sameCategoryListsOtherVisiblePostsOfThatCategoryNewestFirst() throws Exception {
        long dev = category("개발", null);
        long other = category("여행", null);
        long first = publish("첫 글", dev, "PUBLIC");
        long second = publish("둘째 글", dev, "PUBLIC");
        publish("비공개 글", dev, "PRIVATE");
        publish("여행 글", other, "PUBLIC");
        long third = publish("셋째 글", dev, "PUBLIC");
        long uncategorized = publish("미분류 글", null, "PUBLIC");

        mockMvc.perform(get("/api/posts/" + first + "/same-category").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) third, (int) second)));
        mockMvc.perform(get("/api/posts/" + uncategorized + "/same-category")
                        .header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/posts/" + first + "/same-category").param("size", "11")
                        .header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/posts/999999/same-category").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(status().isNotFound());
    }

    private long category(String name, Long parentId) throws Exception {
        String body = parentId == null ? "{\"name\":\"%s\"}".formatted(name)
                : "{\"name\":\"%s\",\"parentId\":%d}".formatted(name, parentId);
        String response = send(post("/api/categories"), ownerCookies, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long publish(String title, Long categoryId, String visibility) throws Exception {
        String body = """
                {"title":"%s","contentHtml":"<p>본문</p>","visibility":"%s","status":"PUBLISHED","categoryId":%s}"""
                .formatted(title, visibility, categoryId == null ? "null" : categoryId.toString());
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                ownerCookies, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long publishTagged(String title, String tagNames) throws Exception {
        String body = """
                {"title":"%s","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"PUBLISHED","tagNames":%s}"""
                .formatted(title, tagNames);
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                ownerCookies, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long tagId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM tag WHERE blog_id = ? AND name = ?", Long.class,
                blog.getId(), name);
    }

    private ResultActions save(long post, String body) throws Exception {
        return send(put("/api/posts/" + post), ownerCookies, body);
    }

    private ResultActions comment(Member member, long post, String body) throws Exception {
        return send(post("/api/posts/" + post + "/comments").header("Idempotency-Key", UUID.randomUUID().toString()),
                testMembers.loginCookies(member), body);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie[] cookies, String body) throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
