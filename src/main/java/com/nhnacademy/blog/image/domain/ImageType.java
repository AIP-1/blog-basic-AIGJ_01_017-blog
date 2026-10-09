package com.nhnacademy.blog.image.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 받는 이미지 형식 (POST-05: jpg/png/gif/webp). 확장자나 브라우저가 보낸 Content-Type은 꾸밀 수 있어서,
 * 파일 앞부분의 매직 넘버(형식마다 정해진 첫 바이트)로 실제 형식을 판단한다.
 * 여기에 더해 파일 이름의 확장자도 그 형식의 것이어야 한다(T036a, R-15, 확장자 허용 목록 + 내용 검사 이중 방어).
 */
public enum ImageType {

    JPEG("image/jpeg", "jpg", "jpg", Set.of("jpg", "jpeg")),
    PNG("image/png", "png", "png", Set.of("png")),
    GIF("image/gif", "gif", "png", Set.of("gif")),
    WEBP("image/webp", "webp", "png", Set.of("webp"));

    private final String contentType;
    private final String extension;
    /** 파일 이름에 붙어도 되는 확장자(소문자). JPEG는 jpg와 jpeg 둘 다. */
    private final Set<String> fileExtensions;
    /** 썸네일 저장 형식. 투명도가 있을 수 있는 형식은 png, 사진(jpg)은 jpg. Java는 webp를 쓸 수 없다(R-15). */
    private final String thumbnailFormat;

    ImageType(String contentType, String extension, String thumbnailFormat, Set<String> fileExtensions) {
        this.contentType = contentType;
        this.extension = extension;
        this.thumbnailFormat = thumbnailFormat;
        this.fileExtensions = fileExtensions;
    }

    /**
     * 파일 이름의 확장자가 이 형식의 것인가. 대소문자는 무시한다(PHOTO.JPG도 된다).
     * 이름이 없거나, 확장자가 없거나(a), 다른 형식의 확장자(PNG 내용인데 a.jpg)거나, 허용 목록 밖(a.html)이면 false.
     */
    public boolean matchesFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return fileExtensions.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** 첫 12바이트로 형식을 알아낸다. 모르는 형식이면 빈 값. */
    public static Optional<ImageType> detect(byte[] head) {
        return Arrays.stream(values()).filter(type -> type.matches(head)).findFirst();
    }

    private boolean matches(byte[] h) {
        return switch (this) {
            case JPEG -> h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case PNG -> h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
            case GIF -> h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                    && (h[4] == '7' || h[4] == '9') && h[5] == 'a';
            case WEBP -> h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        };
    }

    /** 원본을 줄여 다시 저장해도 되는 형식인가. GIF는 애니메이션, WebP는 Java로 쓸 수 없어서 원본 그대로 둔다. */
    public boolean resizable() {
        return this == JPEG || this == PNG;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getThumbnailFormat() {
        return thumbnailFormat;
    }

}
