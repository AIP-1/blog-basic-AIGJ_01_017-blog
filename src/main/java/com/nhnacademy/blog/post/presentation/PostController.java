package com.nhnacademy.blog.post.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.application.PostQueryService;
import com.nhnacademy.blog.post.domain.Post;
import java.util.Map;
import org.springframework.data.domain.Page;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.presentation.dto.PostDetailResponse;
import com.nhnacademy.blog.reaction.application.LikeService;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 글 목록 (T024, BLOG-03, 카테고리별 CAT-02, 태그별 T039 TAG-02)과 글 상세 (T041, POST-04).
 * 블로그 주소에서 부른다.
 */
@RestController
public class PostController {

    private final PostQueryService postQueryService;
    private final PostReadService postReadService;
    private final PostThumbnails postThumbnails;
    private final LikeService likeService;

    public PostController(PostQueryService postQueryService, PostReadService postReadService,
                          PostThumbnails postThumbnails, LikeService likeService) {
        this.postQueryService = postQueryService;
        this.postReadService = postReadService;
        this.postThumbnails = postThumbnails;
        this.likeService = likeService;
    }

    @GetMapping("/api/posts")
    public PageResponse<PostSummaryResponse> posts(@CurrentBlog Blog blog,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) String tag) {
        PageQuery pageQuery = PageQuery.of(page, size, PostQueryService.BLOG_PAGE_SIZE);
        Page<Post> posts = postQueryService.blogPosts(blog, LoginMembers.currentId(), categoryId, tag, pageQuery);
        Map<Long, String> thumbnails = postThumbnails.of(posts.getContent());
        return PageResponse.from(posts, post -> PostSummaryResponse.of(post, blog, thumbnails.get(post.getId())));
    }

    /** 글 상세. 볼 수 없으면 404, 구독자 공개 글을 구독 안 한 사람이 열면 403 SUBSCRIBERS_ONLY. */
    @GetMapping("/api/posts/{id}")
    public PostDetailResponse post(@CurrentBlog Blog blog, @PathVariable Long id) {
        Long viewerId = LoginMembers.currentId();
        return PostDetailResponse.from(postReadService.detail(blog, id, viewerId), likeService.likes(id, viewerId));
    }

}
