package com.nhnacademy.blog.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void removesScript() {
        String result = sanitizer.sanitize("<p>안녕</p><script>alert('xss')</script>");

        assertThat(result).isEqualTo("<p>안녕</p>");
    }

    @Test
    void removesEventAttributesAndStyle() {
        String result = sanitizer.sanitize("<p onclick=\"alert(1)\" style=\"color:red\">글</p>"
                + "<img src=\"/uploads/abc.png\" onerror=\"alert(1)\">");

        assertThat(result)
                .doesNotContain("onclick", "onerror", "style", "alert")
                .contains("<p>글</p>")
                .contains("src=\"/uploads/abc.png\"");
    }

    @Test
    void keepsAllowedFormatting() {
        String html = "<h2>제목</h2><p><strong>굵게</strong> <em>기울임</em></p>"
                + "<ul><li>하나</li></ul><ol><li>둘</li></ol><blockquote><p>인용</p></blockquote>"
                + "<pre><code class=\"language-java\">int a;</code></pre>";

        assertThat(sanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    void escapesSpecialCharactersInCode() {
        // = 같은 글자는 &#61;로 바뀌지만 화면에는 그대로 보인다
        assertThat(sanitizer.sanitize("<pre><code>a = b &lt;script&gt;</code></pre>"))
                .isEqualTo("<pre><code>a &#61; b &lt;script&gt;</code></pre>");
    }

    @Test
    void keepsOnlyHttpLinksWithSafeRel() {
        String result = sanitizer.sanitize("<a href=\"https://spring.io\">ok</a>"
                + "<a href=\"javascript:alert(1)\">bad</a><a href=\"/relative\">rel</a>");

        assertThat(result)
                .contains("href=\"https://spring.io\"")
                .contains("rel=\"nofollow noopener noreferrer\"")
                .doesNotContain("javascript", "/relative");
    }

    @Test
    void keepsOnlyUploadedImages() {
        String result = sanitizer.sanitize("<img src=\"/uploads/9f1c-uuid.webp\" alt=\"사진\">"
                + "<img src=\"https://evil.example/track.png\"><img src=\"data:image/png;base64,AAAA\">");

        assertThat(result)
                .contains("src=\"/uploads/9f1c-uuid.webp\"")
                .doesNotContain("evil.example", "data:");
    }

    @Test
    void removesUnknownTagsButKeepsText() {
        assertThat(sanitizer.sanitize("<div><span>글자</span></div><iframe src=\"https://x\"></iframe>"))
                .isEqualTo("글자");
    }

    @Test
    void codeClassMustBeLanguage() {
        assertThat(sanitizer.sanitize("<code class=\"x\" onclick=\"a()\">c</code>")).isEqualTo("<code>c</code>");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }

}
