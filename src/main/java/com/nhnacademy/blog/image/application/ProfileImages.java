package com.nhnacademy.blog.image.application;

import com.nhnacademy.blog.image.domain.Image;
import com.nhnacademy.blog.image.domain.ImageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 이미지 주소 (BLOG-02). 블로그 프로필은 64px 동그라미라 원본 대신 썸네일(긴 변 400px) 주소를 준다.
 * 회원 프로필 사진(GET /api/me)과 같은 규칙이다.
 */
@Component
public class ProfileImages {

    private final ImageRepository imageRepository;

    public ProfileImages(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    /** 이미지 id가 없거나 이미지 행이 없으면 null. */
    @Transactional(readOnly = true)
    public String thumbnailUrl(Long imageId) {
        if (imageId == null) {
            return null;
        }
        return imageRepository.findById(imageId).map(Image::getThumbnailPath).orElse(null);
    }

}
