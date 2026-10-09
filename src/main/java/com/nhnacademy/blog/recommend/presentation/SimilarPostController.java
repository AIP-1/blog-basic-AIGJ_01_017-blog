package com.nhnacademy.blog.recommend.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import com.nhnacademy.blog.recommend.application.SimilarPostService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비슷한 글 (T069c, OWN-06). 블로그 주소에서 부른다. GET /api/posts/{id}/similar?size=5 → PostSummary[].
 */
@RestController
public class SimilarPostController {

    public static final int DEFAULT_SIZE = 5;
    public static final int MAX_SIZE = 10;

    private final SimilarPostService similarPostService;
    private final PostThumbnails postThumbnails;

    public SimilarPostController(SimilarPostService similarPostService, PostThumbnails postThumbnails) {
        this.similarPostService = similarPostService;
        this.postThumbnails = postThumbnails;
    }

    /** size는 1~10, 없으면 5. 밖이면 400(size). */
    @GetMapping("/api/posts/{id}/similar")
    public List<PostSummaryResponse> similar(@CurrentBlog Blog blog, @PathVariable Long id,
                                             @RequestParam(required = false) Integer size) {
        int count = size == null ? DEFAULT_SIZE : size;
        if (count < 1 || count > MAX_SIZE) {
            throw BusinessException.invalidField("size", "1~" + MAX_SIZE + "개까지 볼 수 있습니다.");
        }
        List<Post> posts = similarPostService.similar(blog, id, LoginMembers.currentId(), count);
        Map<Long, String> thumbnails = postThumbnails.of(posts);
        return posts.stream()
                .map(post -> PostSummaryResponse.of(post, post.getBlog(), thumbnails.get(post.getId())))
                .toList();
    }

}
