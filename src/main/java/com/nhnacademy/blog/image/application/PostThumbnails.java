package com.nhnacademy.blog.image.application;

import com.nhnacademy.blog.image.domain.Image;
import com.nhnacademy.blog.image.domain.ImageRepository;
import com.nhnacademy.blog.post.domain.Post;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목록 한 줄의 썸네일 (POST-05: 목록에는 작게 줄인 이미지). 본문의 첫 이미지를 찾아 그 썸네일 주소를 준다.
 * 대표 이미지 고르기(POST-07)는 백로그(T058)라 아직 thumbnail_image_id를 보지 않는다.
 * 한 페이지의 글을 한꺼번에 받아 이미지 쿼리를 한 번만 한다(N+1 방지).
 */
@Component
public class PostThumbnails {

    /** 서버 정화를 거친 본문이라 img는 src="/uploads/..." 꼴만 남아 있다(HtmlSanitizer). */
    private static final Pattern FIRST_IMAGE = Pattern.compile("<img[^>]*\\ssrc=\"(/uploads/[^\"]+)\"");

    private final ImageRepository imageRepository;

    public PostThumbnails(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    /** 글 id → 썸네일 주소. 이미지가 없는 글은 맵에 없다. */
    @Transactional(readOnly = true)
    public Map<Long, String> of(Collection<Post> posts) {
        Map<Long, String> firstImageByPost = new HashMap<>();
        for (Post post : posts) {
            firstImage(post.getContentHtml()).ifPresent(path -> firstImageByPost.put(post.getId(), path));
        }
        if (firstImageByPost.isEmpty()) {
            return Map.of();
        }
        Map<String, Image> images = imageRepository.findByPathIn(firstImageByPost.values()).stream()
                .collect(Collectors.toMap(Image::getPath, Function.identity(), (first, second) -> first));
        Map<Long, String> thumbnails = new HashMap<>();
        firstImageByPost.forEach((postId, path) -> {
            Image image = images.get(path);
            if (image != null && image.getThumbnailPath() != null) {
                thumbnails.put(postId, image.getThumbnailPath());
            }
        });
        return thumbnails;
    }

    static Optional<String> firstImage(String html) {
        if (html == null) {
            return Optional.empty();
        }
        Matcher matcher = FIRST_IMAGE.matcher(html);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

}
