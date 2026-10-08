package com.nhnacademy.blog.global.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 업로드 이미지를 두는 폴더 (app.upload.dir, plan 저장소). 개발은 코드 저장소 아래 uploads/(git 제외), 운영은 서버 경로.
 * 화면에서는 /uploads/{파일명}으로 연다(WebConfig).
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(Path dir) {
}
