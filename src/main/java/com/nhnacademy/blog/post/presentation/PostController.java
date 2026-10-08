package com.nhnacademy.blog.post.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.post.application.PostQueryService;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.presentation.dto.PostDetailResponse;
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

    public PostController(PostQueryService postQueryService, PostReadService postReadService) {
        this.postQueryService = postQueryService;
        this.postReadService = postReadService;
    }

    @GetMapping("/api/posts")
    public PageResponse<PostSummaryResponse> posts(@CurrentBlog Blog blog,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) String tag) {
        PageQuery pageQuery = PageQuery.of(page, size, PostQueryService.BLOG_PAGE_SIZE);
        return PageResponse.from(
                postQueryService.blogPosts(blog, LoginMembers.currentId(), categoryId, tag, pageQuery),
                post -> PostSummaryResponse.of(post, blog));
    }

    /** 글 상세. 볼 수 없으면 404, 구독자 공개 글을 구독 안 한 사람이 열면 403 SUBSCRIBERS_ONLY. */
    @GetMapping("/api/posts/{id}")
    public PostDetailResponse post(@CurrentBlog Blog blog, @PathVariable Long id) {
        return PostDetailResponse.from(postReadService.detail(blog, id, LoginMembers.currentId()));
    }

}
