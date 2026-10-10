package com.nhnacademy.blog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * 서비스 관리 (스텝 18: T094 회원 정지, T095 숨김, T109 블로그 제한, T110 신고, T111 공지, T112 이력·대시보드,
 * spec US9·US14). 테스트들이 DB를 함께 쓰므로 대시보드는 늘어난 수로 확인한다.
 */
class AdminIntegrationTest extends IntegrationTestSupport {

    private static final String PLATFORM = "blog.test";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member admin;
    Member owner;
    Member reader;
    Blog blog;
    Cookie[] adminCookies;
    Cookie[] ownerCookies;
    Cookie[] readerCookies;

    @BeforeEach
    void setUp() {
        admin = testMembers.admin();
        owner = testMembers.create();
        reader = testMembers.create();
        blog = testBlogs.createPrimary(owner);
        adminCookies = testMembers.loginCookies(admin);
        ownerCookies = testMembers.loginCookies(owner);
        readerCookies = testMembers.loginCookies(reader);
    }

    // ---------- ADMIN-01 영역 ----------

    @Test
    void onlyAdminsUseAdminApis() throws Exception {
        send(get("/api/admin/dashboard"), PLATFORM, null, null).andExpect(status().isUnauthorized());
        send(get("/api/admin/dashboard"), PLATFORM, readerCookies, null).andExpect(status().isForbidden());
        send(post("/api/admin/notices"), PLATFORM, readerCookies, "{\"title\":\"t\",\"content\":\"c\"}")
                .andExpect(status().isForbidden());
        send(get("/api/admin/dashboard"), PLATFORM, adminCookies, null).andExpect(status().isOk());
    }

    // ---------- ADMIN-02 회원 정지 ----------

    @Test
    void suspendHidesBlogsBlocksNextRequestAndIsLoggedAndNotified() throws Exception {
        long post = publish("정지 전 글");

        send(get("/api/admin/members").param("q", owner.getEmail().substring(0, 8)), PLATFORM, adminCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(owner.getId()))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        send(post("/api/admin/members/" + owner.getId() + "/suspension"), PLATFORM, adminCookies,
                "{\"period\":\"7D\",\"reason\":\"ETC\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reasonDetail"));
        send(post("/api/admin/members/" + owner.getId() + "/suspension"), PLATFORM, adminCookies,
                "{\"period\":\"1D\",\"reason\":\"SPAM\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("period"));
        send(post("/api/admin/members/999999999/suspension"), PLATFORM, adminCookies,
                "{\"period\":\"7D\",\"reason\":\"SPAM\"}").andExpect(status().isNotFound());
        send(post("/api/admin/members/" + admin.getId() + "/suspension"), PLATFORM, adminCookies,
                "{\"period\":\"7D\",\"reason\":\"SPAM\"}").andExpect(status().isBadRequest());

        send(post("/api/admin/members/" + owner.getId() + "/suspension"), PLATFORM, adminCookies,
                "{\"period\":\"7D\",\"reason\":\"SPAM\"}").andExpect(status().isNoContent());

        // 이미 로그인해 있던 주인은 다음 요청부터 막히고, 블로그와 글은 다른 사람에게 404
        send(get("/api/me"), PLATFORM, ownerCookies, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.detail.reason").value("SPAM"));
        send(get("/api/blog"), TestBlogs.host(blog), readerCookies, null).andExpect(status().isNotFound());
        send(get("/api/posts/" + post), TestBlogs.host(blog), null, null).andExpect(status().isNotFound());

        send(get("/api/admin/members").param("status", "SUSPENDED").param("q", owner.getNickname()), PLATFORM,
                adminCookies, null)
                .andExpect(jsonPath("$.content[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.content[0].suspendedUntil").isNotEmpty());
        send(get("/api/admin/members/" + owner.getId()), PLATFORM, adminCookies, null)
                .andExpect(jsonPath("$.member.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.blogs[0].address").value(blog.getAddress()))
                .andExpect(jsonPath("$.suspension.reason").value("SPAM"))
                .andExpect(jsonPath("$.moderations[0].action").value("SUSPEND"))
                .andExpect(jsonPath("$.moderations[0].admin.id").value(admin.getId()));
        assertThat(sanctionNotifications(owner)).isEqualTo(1);

        // 해제: 다시 쓸 수 있고, 이력과 알림이 하나씩 더
        send(delete("/api/admin/members/" + owner.getId() + "/suspension"), PLATFORM, adminCookies, null)
                .andExpect(status().isNoContent());
        send(get("/api/me"), PLATFORM, ownerCookies, null).andExpect(status().isOk());
        send(get("/api/blog"), TestBlogs.host(blog), readerCookies, null).andExpect(status().isOk());
        send(delete("/api/admin/members/" + owner.getId() + "/suspension"), PLATFORM, adminCookies, null)
                .andExpect(status().isNoContent());
        assertThat(logCount("MEMBER", owner.getId())).isEqualTo(2);
        assertThat(sanctionNotifications(owner)).isEqualTo(2);
    }

    @Test
    void suspensionEndsByItselfWhenThePeriodIsOver() throws Exception {
        send(post("/api/admin/members/" + owner.getId() + "/suspension"), PLATFORM, adminCookies,
                "{\"period\":\"30D\",\"reason\":\"ABUSE\"}").andExpect(status().isNoContent());
        jdbcTemplate.update("UPDATE member SET suspended_until = NOW() - INTERVAL 1 MINUTE WHERE id = ?", owner.getId());

        send(get("/api/me"), PLATFORM, ownerCookies, null).andExpect(status().isOk());
        send(get("/api/blog"), TestBlogs.host(blog), readerCookies, null).andExpect(status().isOk());
        send(get("/api/admin/members/" + owner.getId()), PLATFORM, adminCookies, null)
                .andExpect(jsonPath("$.member.status").value("ACTIVE"))
                .andExpect(jsonPath("$.suspension").value(nullValue()));
    }

    // ---------- ADMIN-03 숨김, ADMIN-05 블로그 제한 ----------

    @Test
    void blindedPostIsNotFoundForOthersAndShowsReasonToAuthor() throws Exception {
        long post = publish("숨길 글");
        Object updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM post WHERE id = ?", Object.class, post);
        send(post("/api/admin/posts/" + post + "/blind"), PLATFORM, adminCookies, "{\"reason\":\"COPYRIGHT\"}")
                .andExpect(status().isNoContent());

        send(get("/api/posts/" + post), TestBlogs.host(blog), readerCookies, null).andExpect(status().isNotFound());
        send(get("/api/posts/" + post), TestBlogs.host(blog), ownerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blind.reason").value("COPYRIGHT"));
        assertThat(sanctionNotifications(owner)).isEqualTo(1);

        send(delete("/api/admin/posts/" + post + "/blind"), PLATFORM, adminCookies, null)
                .andExpect(status().isNoContent());
        send(get("/api/posts/" + post), TestBlogs.host(blog), readerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));
        // 관리자 조치는 작성자가 고친 것이 아니라 수정 시각이 그대로다
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM post WHERE id = ?", Object.class, post))
                .isEqualTo(updatedAt);
        send(post("/api/admin/posts/999999999/blind"), PLATFORM, adminCookies, "{\"reason\":\"SPAM\"}")
                .andExpect(status().isNotFound());
        send(post("/api/admin/posts/" + post + "/blind"), PLATFORM, adminCookies, "{\"reason\":\"NOPE\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void blindedCommentShowsAsBlindedToOthers() throws Exception {
        long post = publish("댓글 달릴 글");
        long comment = comment(reader, post, "광고 댓글");
        send(post("/api/admin/comments/" + comment + "/blind"), PLATFORM, adminCookies, "{\"reason\":\"SPAM\"}")
                .andExpect(status().isNoContent());

        send(get("/api/posts/" + post + "/comments"), TestBlogs.host(blog), ownerCookies, null)
                .andExpect(jsonPath("$.content[0].state").value("BLINDED"))
                .andExpect(jsonPath("$.content[0].content").value(nullValue()));
        send(get("/api/posts/" + post + "/comments"), TestBlogs.host(blog), readerCookies, null)
                .andExpect(jsonPath("$.content[0].state").value("NORMAL"))
                .andExpect(jsonPath("$.content[0].blind.reason").value("SPAM"));
        assertThat(sanctionNotifications(reader)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at = created_at FROM comment WHERE id = ?", Boolean.class,
                comment)).isTrue();
    }

    @Test
    void restrictedBlogIsNotFoundForOthersAndOwnerSeesReason() throws Exception {
        send(post("/api/admin/blogs/" + blog.getId() + "/restriction"), PLATFORM, adminCookies,
                "{\"reason\":\"ADULT\"}").andExpect(status().isNoContent());

        send(get("/api/blog"), TestBlogs.host(blog), readerCookies, null).andExpect(status().isNotFound());
        send(get("/api/blog"), TestBlogs.host(blog), ownerCookies, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restriction.reason").value("ADULT"));
        // 회원 자체는 제한되지 않는다
        send(get("/api/me"), PLATFORM, ownerCookies, null).andExpect(status().isOk());

        send(delete("/api/admin/blogs/" + blog.getId() + "/restriction"), PLATFORM, adminCookies, null)
                .andExpect(status().isNoContent());
        send(get("/api/blog"), TestBlogs.host(blog), readerCookies, null).andExpect(status().isOk());
        assertThat(logCount("BLOG", blog.getId())).isEqualTo(2);
    }

    // ---------- ADMIN-04 신고 ----------

    @Test
    void reportOnceAndAdminResolvesAllReportsOfTheTarget() throws Exception {
        long post = publish("신고될 글");
        Member another = testMembers.create();

        String body = "{\"targetType\":\"POST\",\"targetId\":" + post + ",\"reason\":\"COPYRIGHT\"}";
        send(post("/api/reports"), PLATFORM, null, body).andExpect(status().isUnauthorized());
        send(post("/api/reports"), PLATFORM, readerCookies,
                "{\"targetType\":\"POST\",\"targetId\":" + post + ",\"reason\":\"ETC\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
        send(post("/api/reports"), PLATFORM, readerCookies, body).andExpect(status().isCreated());
        send(post("/api/reports"), PLATFORM, readerCookies, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        send(post("/api/reports"), PLATFORM, testMembers.loginCookies(another),
                "{\"targetType\":\"POST\",\"targetId\":" + post + ",\"reason\":\"ETC\",\"description\":\"퍼 온 사진\"}")
                .andExpect(status().isCreated());
        send(post("/api/reports"), PLATFORM, readerCookies,
                "{\"targetType\":\"POST\",\"targetId\":999999999,\"reason\":\"SPAM\"}")
                .andExpect(status().isNotFound());
        send(post("/api/reports"), PLATFORM, readerCookies,
                "{\"targetType\":\"BLOG\",\"targetId\":" + blog.getId() + ",\"reason\":\"SPAM\"}")
                .andExpect(status().isCreated());

        // 대상별 묶음, 신고 수 많은 순(이 글 2건이 이 블로그 1건보다 앞)
        String pending = send(get("/api/admin/reports").param("size", "50"), PLATFORM, adminCookies, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = JsonPath.read(pending, "$.content[*].targetId");
        java.util.List<String> types = JsonPath.read(pending, "$.content[*].targetType");
        int postIndex = indexOf(types, ids, "POST", post);
        int blogIndex = indexOf(types, ids, "BLOG", blog.getId());
        assertThat(postIndex).isLessThan(blogIndex);
        assertThat((Integer) JsonPath.read(pending, "$.content[" + postIndex + "].reportCount")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(pending, "$.content[" + postIndex + "].reasons.COPYRIGHT")).isEqualTo(1);
        assertThat((String) JsonPath.read(pending, "$.content[" + postIndex + "].targetPreview")).isEqualTo("신고될 글");

        send(get("/api/admin/reports/POST/" + post), PLATFORM, adminCookies, null)
                .andExpect(jsonPath("$.reports[*].reason", contains("COPYRIGHT", "ETC")))
                .andExpect(jsonPath("$.reports[1].description").value("퍼 온 사진"));

        send(post("/api/admin/reports/POST/" + post + "/resolve"), PLATFORM, adminCookies,
                "{\"result\":\"BLIND\"}").andExpect(status().isBadRequest());
        send(post("/api/admin/reports/POST/" + post + "/resolve"), PLATFORM, adminCookies,
                "{\"result\":\"BLIND\",\"reason\":\"COPYRIGHT\"}").andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report WHERE target_type = 'POST' AND target_id = ?"
                + " AND status = 'DONE' AND result = 'BLIND'", Integer.class, post)).isEqualTo(2);
        send(get("/api/posts/" + post), TestBlogs.host(blog), readerCookies, null).andExpect(status().isNotFound());
        send(post("/api/admin/reports/POST/" + post + "/resolve"), PLATFORM, adminCookies,
                "{\"result\":\"REJECT\"}").andExpect(status().isNotFound());

        // 블로그 신고 기각: 블로그는 그대로, 이력은 REJECT_REPORT
        send(post("/api/admin/reports/BLOG/" + blog.getId() + "/resolve"), PLATFORM, adminCookies,
                "{\"result\":\"REJECT\"}").andExpect(status().isNoContent());
        send(get("/api/blog"), TestBlogs.host(blog), readerCookies, null).andExpect(status().isOk());
        send(get("/api/admin/moderation-logs").param("targetType", "BLOG").param("targetId", blog.getId().toString()),
                PLATFORM, adminCookies, null)
                .andExpect(jsonPath("$.content[0].action").value("REJECT_REPORT"))
                .andExpect(jsonPath("$.content[0].target.label").value(blog.getName() + " (" + blog.getAddress() + ")"));
        send(get("/api/admin/members/" + owner.getId()), PLATFORM, adminCookies, null)
                .andExpect(jsonPath("$.reportCount").value(3));
    }

    @Test
    void suspendResultSuspendsTheCommentAuthor() throws Exception {
        long post = publish("글");
        long comment = comment(reader, post, "욕설");
        send(post("/api/reports"), PLATFORM, ownerCookies,
                "{\"targetType\":\"COMMENT\",\"targetId\":" + comment + ",\"reason\":\"ABUSE\"}")
                .andExpect(status().isCreated());

        send(post("/api/admin/reports/COMMENT/" + comment + "/resolve"), PLATFORM, adminCookies,
                "{\"result\":\"RESTRICT_BLOG\",\"reason\":\"ABUSE\"}").andExpect(status().isBadRequest());
        send(post("/api/admin/reports/COMMENT/" + comment + "/resolve"), PLATFORM, adminCookies,
                "{\"result\":\"SUSPEND\",\"reason\":\"ABUSE\",\"period\":\"PERMANENT\"}")
                .andExpect(status().isNoContent());

        send(get("/api/me"), PLATFORM, readerCookies, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail.suspendedUntil").value(nullValue()));
    }

    @Test
    void cannotReportWhatYouCannotSee() throws Exception {
        long post = publish("비공개로 바꿀 글");
        long secret = comment(owner, post, "비밀", true);
        send(post("/api/reports"), PLATFORM, readerCookies,
                "{\"targetType\":\"COMMENT\",\"targetId\":" + secret + ",\"reason\":\"SPAM\"}")
                .andExpect(status().isNotFound());
        send(patch("/api/posts/" + post + "/visibility"), TestBlogs.host(blog), ownerCookies,
                "{\"visibility\":\"PRIVATE\"}").andExpect(status().isNoContent());
        send(post("/api/reports"), PLATFORM, readerCookies,
                "{\"targetType\":\"POST\",\"targetId\":" + post + ",\"reason\":\"SPAM\"}")
                .andExpect(status().isNotFound());
        send(post("/api/reports"), PLATFORM, readerCookies,
                "{\"targetType\":\"MEMBER\",\"targetId\":1,\"reason\":\"SPAM\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- ADMIN-06 공지, 이력, 대시보드 ----------

    @Test
    void noticesAreWrittenByAdminsAndReadByEveryone() throws Exception {
        send(post("/api/admin/notices"), PLATFORM, adminCookies, "{\"title\":\" \",\"content\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", contains("title", "content")));
        String created = send(post("/api/admin/notices"), PLATFORM, adminCookies,
                "{\"title\":\"정기 점검 안내\",\"content\":\"02:00~04:00\\n쓸 수 없습니다.\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        send(get("/api/notices/latest"), PLATFORM, null, null).andExpect(jsonPath("$.id").value(id));
        send(get("/api/notices"), PLATFORM, null, null).andExpect(jsonPath("$.content[0].title").value("정기 점검 안내"));
        send(put("/api/admin/notices/" + id), PLATFORM, adminCookies, "{\"title\":\"점검 시간 변경\",\"content\":\"03:00\"}")
                .andExpect(status().isNoContent());
        send(get("/api/notices/" + id), PLATFORM, null, null)
                .andExpect(jsonPath("$.title").value("점검 시간 변경"))
                .andExpect(jsonPath("$.content").value("03:00"));
        send(delete("/api/admin/notices/" + id), PLATFORM, adminCookies, null).andExpect(status().isNoContent());
        send(get("/api/notices/" + id), PLATFORM, null, null).andExpect(status().isNotFound());
    }

    @Test
    void dashboardCountsTodayAndShowsRecentModerations() throws Exception {
        String before = send(get("/api/admin/dashboard"), PLATFORM, adminCookies, null)
                .andReturn().getResponse().getContentAsString();
        long post = publish("오늘 글");
        testMembers.create();
        send(post("/api/reports"), PLATFORM, readerCookies,
                "{\"targetType\":\"POST\",\"targetId\":" + post + ",\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        send(post("/api/admin/blogs/" + blog.getId() + "/restriction"), PLATFORM, adminCookies,
                "{\"reason\":\"SPAM\"}").andExpect(status().isNoContent());

        String after = send(get("/api/admin/dashboard"), PLATFORM, adminCookies, null)
                .andReturn().getResponse().getContentAsString();
        assertThat(count(after, "todayPosts")).isEqualTo(count(before, "todayPosts") + 1);
        assertThat(count(after, "todaySignups")).isGreaterThanOrEqualTo(count(before, "todaySignups") + 1);
        assertThat(count(after, "pendingReports")).isEqualTo(count(before, "pendingReports") + 1);
        assertThat((String) JsonPath.read(after, "$.recentModerations[0].action")).isEqualTo("RESTRICT_BLOG");

        send(get("/api/admin/moderation-logs").param("adminId", admin.getId().toString()).param("from", "2026-13-01"),
                PLATFORM, adminCookies, null).andExpect(status().isBadRequest());
        send(get("/api/admin/moderation-logs").param("targetType", "BLOG"), PLATFORM, adminCookies, null)
                .andExpect(jsonPath("$.content[*].target.type", hasItem("BLOG")));
    }

    // ---------- 도우미 ----------

    private long publish(String title) throws Exception {
        String body = """
                {"title":"%s","contentHtml":"<p>본문</p>","visibility":"PUBLIC","status":"PUBLISHED"}""".formatted(title);
        String response = send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                TestBlogs.host(blog), ownerCookies, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long comment(Member member, long post, String content) throws Exception {
        return comment(member, post, content, false);
    }

    private long comment(Member member, long post, String content, boolean secret) throws Exception {
        String response = send(post("/api/posts/" + post + "/comments")
                        .header("Idempotency-Key", UUID.randomUUID().toString()), TestBlogs.host(blog),
                testMembers.loginCookies(member), "{\"content\":\"" + content + "\",\"secret\":" + secret + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private int sanctionNotifications(Member member) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE receiver_id = ? AND type = 'SANCTION'",
                Integer.class, member.getId());
    }

    private int logCount(String targetType, Long targetId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM moderation_log WHERE target_type = ? AND target_id = ?",
                Integer.class, targetType, targetId);
    }

    private static long count(String json, String field) {
        return ((Number) JsonPath.read(json, "$." + field)).longValue();
    }

    private static int indexOf(java.util.List<String> types, java.util.List<Integer> ids, String type, long id) {
        for (int i = 0; i < ids.size(); i++) {
            if (types.get(i).equals(type) && ids.get(i) == id) {
                return i;
            }
        }
        throw new AssertionError(type + " " + id + " 없음");
    }

    private static MockHttpServletRequestBuilder patch(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String host, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, host).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(cookies == null ? request : request.cookie(cookies));
    }

}
