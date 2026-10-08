package com.nhnacademy.blog.image.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 올린 이미지 (POST-05). 파일은 업로드 폴더에 UUID 이름으로 두고, DB에는 주소·원래 이름·크기만 둔다.
 * path·thumbnailPath는 화면에서 여는 주소(/uploads/{파일명})다. 본문 img src와 같은 값이라 본문에서 이미지를 찾을 수 있다.
 * uploader_id는 회원 ↔ 이미지 순환 참조를 피하려고 외래 키 없이 회원 id만 둔다(ERD).
 */
@Entity
@Table(name = "image")
public class Image extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "size", nullable = false)
    private long size;

    protected Image() {
    }

    private Image(Long uploaderId, String path, String thumbnailPath, String originalName, String contentType,
                  long size) {
        this.uploaderId = uploaderId;
        this.path = path;
        this.thumbnailPath = thumbnailPath;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
    }

    public static Image uploaded(Long uploaderId, String path, String thumbnailPath, String originalName,
                                 String contentType, long size) {
        return new Image(uploaderId, path, thumbnailPath, originalName, contentType, size);
    }

    public Long getId() {
        return id;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public String getPath() {
        return path;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

}
