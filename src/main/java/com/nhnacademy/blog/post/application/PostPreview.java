package com.nhnacademy.blog.post.application;

/**
 * 글 링크를 메신저·SNS에 붙였을 때의 미리보기 내용 (T072, SOC-02 공유 미리보기, 원본 4.5).
 * description(요약)과 imagePath(대표 이미지 = 본문 첫 이미지의 썸네일, /uploads/...)는 없으면 null이다.
 */
public record PostPreview(Long postId, String title, String description, String imagePath, String blogName) {
}
