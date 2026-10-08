package com.nhnacademy.blog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.global.config.UploadProperties;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 이미지 올리기 (T036, POST-05, research R-15, spec US2 시나리오 6).
 */
class ImageUploadIntegrationTest extends IntegrationTestSupport {

    /** 1x1 WebP(손실 압축). Java로는 WebP를 쓸 수 없어 미리 만든 바이트를 쓴다. */
    private static final String TINY_WEBP = "UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAwA0JaQAA3AA/vuUAAA=";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    UploadProperties uploadProperties;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member member;
    Cookie[] cookies;

    @BeforeEach
    void setUp() {
        member = testMembers.create();
        cookies = testMembers.loginCookies(member);
    }

    @Test
    void bigJpegIsShrunkAndThumbnailed() throws Exception {
        String body = upload(file("photo.jpg", "image/jpeg", image(3000, 1000, "jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(matchesPattern("^/uploads/[0-9a-f-]{36}\\.jpg$")))
                .andExpect(jsonPath("$.thumbnailUrl").value(matchesPattern("^/uploads/t_[0-9a-f-]{36}\\.jpg$")))
                .andReturn().getResponse().getContentAsString();

        BufferedImage original = read(JsonPath.read(body, "$.url"));
        BufferedImage thumbnail = read(JsonPath.read(body, "$.thumbnailUrl"));
        assertThat(original.getWidth()).isEqualTo(1920);
        assertThat(original.getHeight()).isEqualTo(640);
        assertThat(thumbnail.getWidth()).isEqualTo(400);

        Number id = JsonPath.read(body, "$.id");
        assertThat(jdbcTemplate.queryForMap("SELECT uploader_id, original_name, content_type FROM image WHERE id = ?",
                id.longValue()))
                .containsEntry("uploader_id", member.getId())
                .containsEntry("original_name", "photo.jpg")
                .containsEntry("content_type", "image/jpeg");
    }

    @Test
    void smallPngIsNotEnlargedAndIsServed() throws Exception {
        String body = upload(file("small.png", "image/png", image(50, 30, "png")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.thumbnailUrl").value(endsWith(".png")))
                .andReturn().getResponse().getContentAsString();

        assertThat(read(JsonPath.read(body, "$.thumbnailUrl")).getWidth()).isEqualTo(50);
        mockMvc.perform(get((String) JsonPath.read(body, "$.url")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("max-age")));
    }

    @Test
    void gifIsKeptAsIsAndWebpIsAccepted() throws Exception {
        byte[] gif = image(20, 20, "gif");
        String gifBody = upload(file("anim.gif", "image/gif", gif)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(Files.readAllBytes(stored(JsonPath.read(gifBody, "$.url")))).isEqualTo(gif);
        assertThat((String) JsonPath.read(gifBody, "$.thumbnailUrl")).endsWith(".png");

        byte[] webp = Base64.getDecoder().decode(TINY_WEBP);
        String webpBody = upload(file("tiny.webp", "image/webp", webp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(endsWith(".webp")))
                .andReturn().getResponse().getContentAsString();
        assertThat(read(JsonPath.read(webpBody, "$.thumbnailUrl")).getWidth()).isEqualTo(1);
    }

    @Test
    void disguisedOrBrokenFilesAreRejected() throws Exception {
        // 확장자와 Content-Type만 이미지인 글자 파일
        upload(file("evil.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        // PNG 머리만 붙이고 나머지는 엉터리
        byte[] fakePng = new byte[64];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, fakePng, 0, 8);
        upload(file("broken.png", "image/png", fakePng))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        // BMP는 받지 않는 형식
        upload(file("old.bmp", "image/bmp", image(10, 10, "bmp")))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }

    @Test
    void overTenMegabytesIsRejected() throws Exception {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(image(10, 10, "png"), 0, big, 0, 8);

        upload(file("big.png", "image/png", big))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
    }

    @Test
    void loginIsRequired() throws Exception {
        mockMvc.perform(multipart("/api/images").file(file("a.png", "image/png", image(5, 5, "png")))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions upload(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/images").file(file)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(cookies));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private static byte[] image(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private Path stored(String url) {
        return uploadProperties.dir().resolve(url.substring("/uploads/".length()));
    }

    private BufferedImage read(String url) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(stored(url))));
    }

}
