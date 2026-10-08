package com.nhnacademy.blog.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SummaryExtractorTest {

    private final SummaryExtractor extractor = new SummaryExtractor();

    @Test
    void stripsTags() {
        assertThat(extractor.extract("<h2>필터 체인</h2><p>Spring <strong>Security</strong>는</p><pre><code>x</code></pre>"))
                .isEqualTo("필터 체인 Spring Security는 x");
    }

    @Test
    void emptyForNullOrImagesOnly() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("<p><img src=\"/uploads/a.png\"></p>")).isEmpty();
    }

    @Test
    void cutsLongTextToColumnLength() {
        String result = extractor.extract("<p>" + "가".repeat(400) + "</p>");

        assertThat(result).hasSize(SummaryExtractor.MAX_LENGTH).endsWith("…");
    }

    @Test
    void doesNotSplitEmoji() {
        String result = extractor.extract("<p>" + "😀".repeat(400) + "</p>");

        assertThat(result.codePointCount(0, result.length())).isEqualTo(SummaryExtractor.MAX_LENGTH);
        assertThat(result).endsWith("😀…");
    }

    @Test
    void shortTextIsKeptWhole() {
        assertThat(extractor.extract("<p>짧은 글</p>")).isEqualTo("짧은 글");
    }

}
