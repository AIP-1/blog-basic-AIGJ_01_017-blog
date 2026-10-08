package com.nhnacademy.blog.image.presentation.dto;

import com.nhnacademy.blog.image.domain.Image;

/** 올린 이미지 { id, url, thumbnailUrl }. 에디터는 url을 본문 img src로 넣는다. */
public record ImageResponse(Long id, String url, String thumbnailUrl) {

    public static ImageResponse from(Image image) {
        return new ImageResponse(image.getId(), image.getPath(), image.getThumbnailPath());
    }

}
