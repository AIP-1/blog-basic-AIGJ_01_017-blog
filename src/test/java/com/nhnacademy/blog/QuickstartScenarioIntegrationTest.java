package com.nhnacademy.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.support.TestEmails;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * quickstart.md를 그대로 따라가는 시나리오 (T070). 다른 테스트가 기능별로 자세히 보는 것을, 여기서는 사용자가 실제로 밟는 순서대로
 * 한 번에 지나간다. 가입부터 실제 API로 하고, 응답의 로그인 쿠키를 다음 요청에 붙여 브라우저처럼 움직인다.
 * <p>
 * quickstart의 "주소 영속성(SC-005, P2 이후)"은 글 옮기기·블로그 이사·삭제 기능(BLOG-06·07, 백로그)이 아직 없어 여기서 다루지 않는다.
 * 서버의 301·404 처리는 SpaForwardIntegrationTest(이사 상태를 SQL로 만들어), 삭제된 주소 재사용 거절은 BlogCreateIntegrationTest가 본다.
 */
class QuickstartScenarioIntegrationTest extends IntegrationTestSupport {

    private static final String PLATFORM = "blog.test";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestEmails testEmails;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** quickstart "P0 한 바퀴" 1~6 (spec SC-001, US1~US3). */
    @Test
    void p0Loop() throws Exception {
        // 1. A 가입 → 로그인 → 블로그 개설. 주소는 바꿀 수 없다
        String alpha = "qa" + UUID.randomUUID().toString().substring(0, 8);
        User a = signup("A");
        a = login(a);
        send(post("/api/blogs"), PLATFORM, a.cookies(),
                "{\"address\":\"" + alpha + "\",\"name\":\"A의 블로그\"}").andExpect(status().isCreated());
        String alphaHost = alpha + "." + PLATFORM;
        send(patch("/api/blog"), alphaHost, a.cookies(), "{\"name\":\"A의 기록\",\"address\":\"changed\"}")
                .andExpect(status().isOk());
        send(get("/api/blog"), alphaHost, null, null).andExpect(jsonPath("$.address").value(alpha));

        // 2. 블로그 메인: 빈 상태와 사이드바, 블로그 주소에서도 같은 로그인 상태(쿠키 Domain=.blog.test)
        send(get("/api/posts"), alphaHost, null, null).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/blog/sidebar"), alphaHost, null, null).andExpect(jsonPath("$.modules[0].type").value("PROFILE"));
        send(get("/api/me"), alphaHost, a.cookies(), null).andExpect(jsonPath("$.primaryBlog.address").value(alpha));
        assertThat(a.cookies()).allSatisfy(cookie -> assertThat(cookie.getDomain()).isEqualTo("." + PLATFORM));

        // 3. 카테고리 'Java' → 굵게·목록·코드 블록·이미지·태그 2개 글 발행 → 공감·댓글·조회수 0
        long javaId = idOf(send(post("/api/categories"), alphaHost, a.cookies(), "{\"name\":\"Java\"}")
                .andExpect(status().isCreated()));
        String imageUrl = JsonPath.read(upload(a), "$.url");
        String contentHtml = "<p><strong>굵게</strong></p><ul><li>목록</li></ul><pre><code>int x = 1;</code></pre>"
                + "<img src=\\\"" + imageUrl + "\\\" alt=\\\"사진\\\">";
        long postId = idOf(send(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), alphaHost,
                a.cookies(), """
                        {"title":"첫 글","contentHtml":"%s","categoryId":%d,"tagNames":["java","spring"],
                         "visibility":"PUBLIC","status":"PUBLISHED"}
                        """.formatted(contentHtml, javaId))
                .andExpect(status().isCreated()));
        send(get("/api/posts/" + postId), alphaHost, null, null)
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.category.name").value("Java"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.contentHtml", containsString("<strong>굵게</strong>")))
                .andExpect(jsonPath("$.contentHtml", containsString("<ul><li>목록</li></ul>")))
                .andExpect(jsonPath("$.contentHtml", containsString("<pre><code>")))
                .andExpect(jsonPath("$.contentHtml", containsString(imageUrl)));

        // 4. 로그아웃 → 비회원으로 홈 최신 글에 그 글이 보임
        send(post("/api/auth/logout"), PLATFORM, a.cookies(), null).andExpect(status().isNoContent());
        String latest = send(get("/api/home/latest"), PLATFORM, null, null).andReturn().getResponse()
                .getContentAsString();
        assertThat(ids(latest, "$.content[*].id")).contains(postId);

        // 5. B 가입·로그인 → 글 열람 → 댓글 → 공감, 새로고침해도 1, 다시 눌러도 1
        User b = login(signup("B"));
        send(get("/api/posts/" + postId), alphaHost, b.cookies(), null).andExpect(status().isOk());
        long commentId = idOf(send(post("/api/posts/" + postId + "/comments")
                .header("Idempotency-Key", UUID.randomUUID().toString()), alphaHost, b.cookies(),
                "{\"content\":\"잘 읽었습니다\"}").andExpect(status().isCreated()));
        send(put("/api/posts/" + postId + "/like"), alphaHost, b.cookies(), null).andExpect(jsonPath("$.likeCount").value(1));
        send(put("/api/posts/" + postId + "/like"), alphaHost, b.cookies(), null).andExpect(jsonPath("$.likeCount").value(1));
        send(get("/api/posts/" + postId), alphaHost, b.cookies(), null)
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.viewer.liked").value(true))
                .andExpect(jsonPath("$.commentCount").value(1));

        // 6. A로 로그인 → B 댓글을 지울 수 있고, 수정 버튼은 없음
        a = login(a);
        send(get("/api/posts/" + postId + "/comments"), alphaHost, a.cookies(), null)
                .andExpect(jsonPath("$.content[0].id").value(commentId))
                .andExpect(jsonPath("$.content[0].viewer.canDelete").value(true))
                .andExpect(jsonPath("$.content[0].viewer.canEdit").value(false));
        send(delete("/api/comments/" + commentId), alphaHost, a.cookies(), null).andExpect(status().isNoContent());
        send(get("/api/posts/" + postId), alphaHost, null, null).andExpect(jsonPath("$.commentCount").value(0));
    }

    /** quickstart "권한·가시성" 표 8줄 (US4, SC-002·003·004·006). */
    @Test
    void permissionAndVisibilityTable() throws Exception {
        User a = login(signup("A"));
        String alpha = "qa" + UUID.randomUUID().toString().substring(0, 8);
        send(post("/api/blogs"), PLATFORM, a.cookies(), "{\"address\":\"" + alpha + "\",\"name\":\"A\"}")
                .andExpect(status().isCreated());
        String alphaHost = alpha + "." + PLATFORM;
        User b = login(signup("B"));
        String beta = "qb" + UUID.randomUUID().toString().substring(0, 8);
        send(post("/api/blogs"), PLATFORM, b.cookies(), "{\"address\":\"" + beta + "\",\"name\":\"B\"}")
                .andExpect(status().isCreated());
        String betaHost = beta + "." + PLATFORM;
        long postId = publish(a, alphaHost, "A의 글", "<p>본문</p>", UUID.randomUUID().toString());

        // 비회원이 관리 화면 → 화면은 앱을 주고(앱이 로그인으로 보냄), 관리 API는 401
        send(get("/manage/write"), alphaHost, null, null).andExpect(status().isOk());
        send(get("/api/manage/posts/" + postId), alphaHost, null, null).andExpect(status().isUnauthorized());

        // B가 A 글 수정 → 403
        send(put("/api/posts/" + postId), alphaHost, b.cookies(), """
                {"title":"남의 글","contentHtml":"<p>x</p>","visibility":"PUBLIC","status":"PUBLISHED"}
                """).andExpect(status().isForbidden());

        // B 블로그 주소에 A 글 번호 → 볼 수 있으면 A 블로그로 301
        send(get("/" + postId), betaHost, null, null)
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://" + alphaHost + "/" + postId));

        // A가 비공개로 → B·비회원은 404, 홈·목록·글 수에서 빠짐, 다른 블로그 주소로도 404 (바꾸기 전에는 1)
        send(get("/api/posts"), alphaHost, null, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/categories"), alphaHost, null, null).andExpect(jsonPath("$.totalCount").value(1));
        assertThat(ids(send(get("/api/home/latest"), PLATFORM, null, null).andReturn().getResponse()
                .getContentAsString(), "$.content[*].id")).contains(postId);
        send(patch("/api/posts/" + postId + "/visibility"), alphaHost, a.cookies(), "{\"visibility\":\"PRIVATE\"}")
                .andExpect(status().isNoContent());
        send(get("/api/posts/" + postId), alphaHost, b.cookies(), null).andExpect(status().isNotFound());
        send(get("/api/posts/" + postId), alphaHost, null, null).andExpect(status().isNotFound());
        send(get("/api/posts"), alphaHost, null, null).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/categories"), alphaHost, null, null).andExpect(jsonPath("$.totalCount").value(0));
        assertThat(ids(send(get("/api/home/latest"), PLATFORM, null, null).andReturn().getResponse()
                .getContentAsString(), "$.content[*].id")).doesNotContain(postId);
        send(get("/" + postId), betaHost, null, null).andExpect(status().isNotFound());

        // B가 관리자 API → 403
        send(get("/api/admin/dashboard"), PLATFORM, b.cookies(), null).andExpect(status().isForbidden());

        // 본문의 onerror·script는 저장할 때 지워진다 (SC-006)
        long xss = publish(a, alphaHost, "XSS", "<p>글</p><img src=x onerror=alert(1)><script>alert(2)</script>",
                UUID.randomUUID().toString());
        String saved = jdbcTemplate.queryForObject("SELECT content_html FROM post WHERE id = ?", String.class, xss);
        assertThat(saved).doesNotContain("onerror", "<script", "alert(");

        // 같은 Idempotency-Key로 두 번 발행 → 글 1개 (SC-004)
        String key = UUID.randomUUID().toString();
        long first = publish(a, alphaHost, "한 번만", "<p>x</p>", key);
        long second = publish(a, alphaHost, "한 번만", "<p>x</p>", key);
        assertThat(second).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post WHERE title = '한 번만' AND blog_id = "
                + "(SELECT id FROM blog WHERE address = ?)", Integer.class, alpha)).isEqualTo(1);

        // ?page=999 → 200, 빈 목록
        send(get("/api/posts").param("page", "999"), alphaHost, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private record User(String email, String password, Cookie[] cookies) {
    }

    /** 인증 코드 받기 → 코드 확인 → 가입. 가입하면 바로 로그인된다. */
    private User signup(String who) throws Exception {
        String email = TestEmails.unique();
        String password = "password1";
        send(post("/api/auth/email-verifications"), PLATFORM, null, "{\"email\":\"" + email + "\"}")
                .andExpect(status().isAccepted());
        String code = testEmails.latestCode(email);
        send(post("/api/auth/email-verifications/verify"), PLATFORM, null,
                "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}").andExpect(status().is2xxSuccessful());
        MockHttpServletResponse response = send(post("/api/auth/signup"), PLATFORM, null, """
                {"email":"%s","code":"%s","password":"%s","nickname":"%s"}
                """.formatted(email, code, password, who + UUID.randomUUID().toString().substring(0, 6)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return new User(email, password, liveCookies(response));
    }

    private User login(User user) throws Exception {
        MockHttpServletResponse response = send(post("/api/auth/login"), PLATFORM, null,
                "{\"email\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse();
        return new User(user.email(), user.password(), liveCookies(response));
    }

    private long publish(User user, String host, String title, String html, String key) throws Exception {
        String body = """
                {"title":"%s","contentHtml":"%s","visibility":"PUBLIC","status":"PUBLISHED"}
                """.formatted(title, html.replace("\"", "\\\""));
        return idOf(send(post("/api/posts").header("Idempotency-Key", key), host, user.cookies(), body)
                .andExpect(status().isCreated()));
    }

    private String upload(User user) throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return mockMvc.perform(multipart("/api/images")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", out.toByteArray()))
                        .header(HttpHeaders.HOST, PLATFORM)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(user.cookies()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** 응답이 준 쿠키 중 지우는 쿠키(Max-Age=0)를 뺀 것. 브라우저가 들고 다니는 쿠키다. */
    private static Cookie[] liveCookies(MockHttpServletResponse response) {
        List<Cookie> live = new ArrayList<>();
        for (Cookie cookie : response.getCookies()) {
            if (cookie.getMaxAge() != 0) {
                live.add(cookie);
            }
        }
        return live.toArray(Cookie[]::new);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String host, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, host).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (cookies != null && cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

    private static long idOf(ResultActions created) throws Exception {
        return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static List<Long> ids(String body, String path) {
        List<Number> ids = JsonPath.read(body, path);
        return ids.stream().map(Number::longValue).toList();
    }

}
