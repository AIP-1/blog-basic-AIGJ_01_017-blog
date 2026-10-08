package com.nhnacademy.blog.post.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.post.application.PostQueryService;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 글 목록 (T024, BLOG-03). 블로그 주소에서 부른다. 태그별 목록(tag)은 태그 기능(스텝 7)에서 더한다.
 */
@RestController
public class PostController {

    private final PostQueryService postQueryService;

    public PostController(PostQueryService postQueryService) {
        this.postQueryService = postQueryService;
    }

    @GetMapping("/api/posts")
    public PageResponse<PostSummaryResponse> posts(@CurrentBlog Blog blog,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) Long categoryId) {
        PageQuery pageQuery = PageQuery.of(page, size, PostQueryService.BLOG_PAGE_SIZE);
        return PageResponse.from(
                postQueryService.blogPosts(blog, LoginMembers.currentId(), categoryId, pageQuery),
                post -> PostSummaryResponse.of(post, blog));
    }

}
