package com.nhnacademy.blog.image.presentation;

import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.image.application.ImageService;
import com.nhnacademy.blog.image.presentation.dto.ImageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 올리기 (T036, POST-05). 어느 주소에서 불러도 같다. multipart/form-data의 file 하나.
 */
@RestController
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /** 201 { id, url, thumbnailUrl }. jpg/png/gif/webp가 아니면 400 UNSUPPORTED_IMAGE, 10MB 초과 400 IMAGE_TOO_LARGE. */
    @PostMapping("/api/images")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse upload(@AuthenticationPrincipal LoginMember member,
                                @RequestPart(name = "file", required = false) MultipartFile file) {
        return ImageResponse.from(imageService.upload(member.id(), file));
    }

}
