package com.nhnacademy.blog.image.application;

import com.nhnacademy.blog.image.domain.Image;
import com.nhnacademy.blog.image.domain.ImageRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 이미지 주소 (BLOG-02, AUTH-05). 블로그 프로필·회원 사진은 작은 동그라미라 원본 대신 썸네일(긴 변 400px) 주소를 준다.
 * 회원 프로필 사진(GET /api/me)과 같은 규칙이다. 댓글 작성자처럼 여러 명이면 thumbnailUrls로 한 번에 읽는다.
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

    /**
     * 이미지 id → 썸네일 주소. null id는 건너뛰고, 쿼리는 한 번이다(N+1 방지). 없는 이미지는 맵에 없다.
     * 사진이 없는 회원의 id(null)로 get해도 되도록 null 키를 받는 HashMap을 돌려준다(Map.of()는 get(null)에 NPE).
     */
    @Transactional(readOnly = true)
    public Map<Long, String> thumbnailUrls(Collection<Long> imageIds) {
        var ids = imageIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return imageRepository.findAllById(ids).stream()
                .filter(image -> image.getThumbnailPath() != null)
                .collect(Collectors.toMap(Image::getId, Image::getThumbnailPath, (a, b) -> a, HashMap::new));
    }

}
