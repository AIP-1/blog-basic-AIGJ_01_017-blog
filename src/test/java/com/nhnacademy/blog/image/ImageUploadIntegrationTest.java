package com.nhnacademy.blog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.config.UploadProperties;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
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
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

    @Autowired
    TestBlogs testBlogs;

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
    void fileNameExtensionMustBeAllowedAndMatchTheRealFormat() throws Exception {
        byte[] jpeg = image(20, 20, "jpg");
        byte[] png = image(20, 20, "png");
        // 내용은 진짜 이미지여도 이름의 확장자가 허용 목록 밖이거나 없으면 거절 (T036a)
        for (String name : new String[] {"photo.txt", "photo.html", "photo.svg", "photo", "photo."}) {
            upload(file(name, "image/jpeg", jpeg))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        }
        // 확장자와 실제 형식이 다르면 거절(PNG 내용인데 .jpg, JPEG 내용인데 .gif)
        upload(file("photo.jpg", "image/jpeg", png)).andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        upload(file("photo.gif", "image/gif", jpeg)).andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        // jpg와 jpeg는 같고, 대소문자는 무시한다. 저장 이름은 서버가 정한다
        upload(file("PHOTO.JPEG", "image/jpeg", jpeg)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(endsWith(".jpg")));
        upload(file("photo.Png", "image/png", png)).andExpect(status().isCreated());
    }

    @Test
    void exifOrientationIsAppliedToTheStoredPixels() throws Exception {
        // 가로 40 × 세로 20으로 찍혔지만 EXIF가 "시계 방향으로 90도 돌려 보여라"(Orientation=6)인 휴대폰 사진
        byte[] rotated = withExifOrientation(image(40, 20, "jpg"), 6);

        String body = upload(file("phone.jpg", "image/jpeg", rotated)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 저장된 원본과 썸네일은 돌린 모양(세로 20 × 40)이라, EXIF를 모르는 프로그램에서도 바로 선다
        BufferedImage original = read(JsonPath.read(body, "$.url"));
        assertThat(original.getWidth()).isEqualTo(20);
        assertThat(original.getHeight()).isEqualTo(40);
        BufferedImage thumbnail = read(JsonPath.read(body, "$.thumbnailUrl"));
        assertThat(thumbnail.getWidth()).isLessThan(thumbnail.getHeight());
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
    void listShowsThumbnailOfFirstImageInBody() throws Exception {
        Blog blog = testBlogs.create(member);
        String uploaded = upload(file("a.jpg", "image/jpeg", image(800, 600, "jpg")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(uploaded, "$.url");
        String thumbnailUrl = JsonPath.read(uploaded, "$.thumbnailUrl");
        mockMvc.perform(post("/api/posts")
                        .header("Host", TestBlogs.host(blog))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"사진 글\",\"contentHtml\":\"<p>글</p><img src=\\\"" + url
                                + "\\\" alt=\\\"a\\\">\",\"visibility\":\"PUBLIC\",\"status\":\"PUBLISHED\"}")
                        .cookie(cookies))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts").header("Host", TestBlogs.host(blog)))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value(thumbnailUrl));
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

    /**
     * JPEG의 SOI(FF D8) 바로 뒤에 EXIF(APP1) 조각을 끼운다. 내용은 방향(Orientation, 태그 0x0112) 하나뿐이다.
     * APP1 = FF E1, 길이, "Exif\0\0", TIFF 머리(MM, 42, 첫 IFD 위치 8), IFD 항목 1개(SHORT 1개 = orientation), 다음 IFD 없음.
     */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] app1 = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00};
        byte[] result = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(app1, 0, result, 2, app1.length);
        System.arraycopy(jpeg, 2, result, 2 + app1.length, jpeg.length - 2);
        return result;
    }

    private Path stored(String url) {
        return uploadProperties.dir().resolve(url.substring("/uploads/".length()));
    }

    private BufferedImage read(String url) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(stored(url))));
    }

}
