package com.nhnacademy.blog.image.application;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.image.domain.Image;
import com.nhnacademy.blog.image.domain.ImageRepository;
import com.nhnacademy.blog.post.domain.Post;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 대표 이미지 (POST-05, POST-07). 목록 한 줄의 썸네일과 공유 미리보기 이미지가 이것을 쓴다.
 * 주인이 고른 대표 이미지(thumbnail_image_id)가 있으면 그것, 없으면 본문 첫 이미지의 썸네일이다(T058).
 * 한 페이지의 글을 한꺼번에 받아 이미지 쿼리를 종류마다 한 번만 한다(N+1 방지).
 */
@Component
public class PostThumbnails {

    /** 서버 정화를 거친 본문이라 img는 src="/uploads/..." 꼴만 남아 있다(HtmlSanitizer). */
    private static final Pattern IMAGE_SRC = Pattern.compile("<img[^>]*\\ssrc=\"(/uploads/[^\"]+)\"");

    private final ImageRepository imageRepository;

    public PostThumbnails(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    /** 글 id → 썸네일 주소. 대표 이미지도 본문 이미지도 없는 글은 맵에 없다. */
    @Transactional(readOnly = true)
    public Map<Long, String> of(Collection<Post> posts) {
        Map<Long, String> thumbnails = new HashMap<>();
        List<Post> withoutChosen = new ArrayList<>();

        Set<Long> chosenIds = posts.stream().map(Post::getThumbnailImageId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Image> chosen = chosenIds.isEmpty() ? Map.of() : imageRepository.findAllById(chosenIds).stream()
                .collect(Collectors.toMap(Image::getId, Function.identity()));
        for (Post post : posts) {
            Image image = post.getThumbnailImageId() == null ? null : chosen.get(post.getThumbnailImageId());
            if (image != null && image.getThumbnailPath() != null) {
                thumbnails.put(post.getId(), image.getThumbnailPath());
            } else {
                withoutChosen.add(post);
            }
        }

        Map<Long, String> firstImageByPost = new HashMap<>();
        for (Post post : withoutChosen) {
            firstImage(post.getContentHtml()).ifPresent(path -> firstImageByPost.put(post.getId(), path));
        }
        if (firstImageByPost.isEmpty()) {
            return thumbnails;
        }
        Map<String, Image> images = byPath(firstImageByPost.values());
        firstImageByPost.forEach((postId, path) -> {
            Image image = images.get(path);
            if (image != null && image.getThumbnailPath() != null) {
                thumbnails.put(postId, image.getThumbnailPath());
            }
        });
        return thumbnails;
    }

    /** 본문에 든 이미지, 본문 순서대로(같은 이미지가 두 번 나오면 한 번). 수정 화면이 대표 이미지 후보로 보여 준다. */
    @Transactional(readOnly = true)
    public List<Image> bodyImages(String html) {
        Set<String> paths = imagePaths(html);
        if (paths.isEmpty()) {
            return List.of();
        }
        Map<String, Image> images = byPath(paths);
        return paths.stream().map(images::get).filter(Objects::nonNull).toList();
    }

    /**
     * 대표 이미지로 고른 이미지가 본문에 들어 있는지 확인한다 (contracts 글 저장 본문: 본문에 들어간 이미지 중 하나).
     * null이면 고르지 않은 것이라 통과한다. 없는 이미지거나 본문에 없으면 400.
     *
     * @param html 서버 정화를 거친 본문
     */
    @Transactional(readOnly = true)
    public void requireInBody(Long imageId, String html) {
        if (imageId == null) {
            return;
        }
        boolean inBody = imageRepository.findById(imageId)
                .map(image -> imagePaths(html).contains(image.getPath()))
                .orElse(false);
        if (!inBody) {
            throw BusinessException.invalidField("thumbnailImageId", "본문에 넣은 이미지 중에서 골라 주세요.");
        }
    }

    static Optional<String> firstImage(String html) {
        return imagePaths(html).stream().findFirst();
    }

    private static Set<String> imagePaths(String html) {
        Set<String> paths = new LinkedHashSet<>();
        if (html == null) {
            return paths;
        }
        Matcher matcher = IMAGE_SRC.matcher(html);
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return paths;
    }

    private Map<String, Image> byPath(Collection<String> paths) {
        return imageRepository.findByPathIn(paths).stream()
                .collect(Collectors.toMap(Image::getPath, Function.identity(), (first, second) -> first));
    }

}
