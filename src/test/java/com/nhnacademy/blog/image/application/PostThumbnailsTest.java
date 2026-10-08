package com.nhnacademy.blog.image.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 본문 첫 이미지 찾기 (POST-05 목록 썸네일). */
class PostThumbnailsTest {

    @Test
    void findsFirstUploadedImage() {
        assertThat(PostThumbnails.firstImage("<p>글</p><img src=\"/uploads/a.jpg\" alt=\"x\" /><img src=\"/uploads/b.png\" />"))
                .contains("/uploads/a.jpg");
        assertThat(PostThumbnails.firstImage("<p><img alt=\"x\" src=\"/uploads/c.webp\"></p>")).contains("/uploads/c.webp");
    }

    @Test
    void noImageMeansNoThumbnail() {
        assertThat(PostThumbnails.firstImage("<p>글만</p>")).isEmpty();
        assertThat(PostThumbnails.firstImage(null)).isEmpty();
    }

}
