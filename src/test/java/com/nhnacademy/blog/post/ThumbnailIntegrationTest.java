package com.nhnacademy.blog.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 대표 이미지 (T058, POST-07, spec US5 수용 시나리오 2): 고르면 그 이미지, 안 고르면 본문 첫 이미지, 본문에 없는 이미지는 400.
 */
class ThumbnailIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    Blog blog;
    Cookie[] cookies;
    Uploaded first;
    Uploaded second;

    record Uploaded(long id, String url, String thumbnailUrl) {
    }

    @BeforeEach
    void setUp() throws Exception {
        Member owner = testMembers.create();
        blog = testBlogs.create(owner);
        cookies = testMembers.loginCookies(owner);
        first = upload(cookies);
        second = upload(cookies);
    }

    @Test
    void withoutChoiceFirstBodyImageIsThumbnail() throws Exception {
        create(body(first, second), null);

        send(get("/api/posts"), null, null).andExpect(jsonPath("$.content[0].thumbnailUrl").value(first.thumbnailUrl()));
    }

    @Test
    void chosenBodyImageIsThumbnailEverywhere() throws Exception {
        long id = create(body(first, second), second.id());

        send(get("/api/posts"), null, null)
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value(second.thumbnailUrl()));
        send(get("/api/manage/posts"), cookies, null)
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value(second.thumbnailUrl()));
        // 수정 화면: 고른 이미지와, 후보인 본문 이미지(본문 순서)
        send(get("/api/manage/posts/" + id), cookies, null)
                .andExpect(jsonPath("$.thumbnailImageId").value(second.id()))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].id").value(first.id()))
                .andExpect(jsonPath("$.images[0].url").value(first.url()))
                .andExpect(jsonPath("$.images[0].thumbnailUrl").value(first.thumbnailUrl()))
                .andExpect(jsonPath("$.images[1].id").value(second.id()));
    }

    @Test
    void imageNotInBodyIsRejected() throws Exception {
        save(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), body(first), second.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("thumbnailImageId"));
        save(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()), body(first), 999_999_999L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("thumbnailImageId"));
    }

    @Test
    void editingKeepsRuleAndCanClearChoice() throws Exception {
        long id = create(body(first, second), second.id());

        // 고른 이미지를 본문에서 빼면서 그대로 보내면 400
        save(put("/api/posts/" + id), body(first), second.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("thumbnailImageId"));
        // 고르지 않음(null)으로 바꾸면 다시 본문 첫 이미지
        save(put("/api/posts/" + id), body(second, first), null).andExpect(status().isOk());
        send(get("/api/posts"), null, null).andExpect(jsonPath("$.content[0].thumbnailUrl").value(second.thumbnailUrl()));
        send(get("/api/manage/posts/" + id), cookies, null).andExpect(jsonPath("$.thumbnailImageId").isEmpty());
    }

    private long create(String html, Long thumbnailImageId) throws Exception {
        String response = save(post("/api/posts").header("Idempotency-Key", UUID.randomUUID().toString()),
                html, thumbnailImageId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions save(MockHttpServletRequestBuilder request, String html, Long thumbnailImageId)
            throws Exception {
        return send(request, cookies, """
                {"title":"사진 글","contentHtml":"%s","visibility":"PUBLIC","status":"PUBLISHED","thumbnailImageId":%s}
                """.formatted(html.replace("\"", "\\\""), thumbnailImageId));
    }

    private static String body(Uploaded... images) {
        StringBuilder html = new StringBuilder("<p>글</p>");
        for (Uploaded image : images) {
            html.append("<p><img src=\"").append(image.url()).append("\" alt=\"\"></p>");
        }
        return html.toString();
    }

    private Uploaded upload(Cookie[] cookies) throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String body = mockMvc.perform(multipart("/api/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", out.toByteArray()))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookies))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Uploaded(((Number) JsonPath.read(body, "$.id")).longValue(), JsonPath.read(body, "$.url"),
                JsonPath.read(body, "$.thumbnailUrl"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie[] cookies, String body)
            throws Exception {
        request.header(HttpHeaders.HOST, TestBlogs.host(blog)).header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (cookies != null) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

}
