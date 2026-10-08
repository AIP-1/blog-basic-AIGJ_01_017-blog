package com.nhnacademy.blog.search.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import com.nhnacademy.blog.search.application.SearchService;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 안 검색 (T047, SRCH-01). 블로그 주소에서 부른다. 전체 검색(SRCH-02, 플랫폼 주소)은 백로그다.
 */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final PostThumbnails postThumbnails;

    public SearchController(SearchService searchService, PostThumbnails postThumbnails) {
        this.searchService = searchService;
        this.postThumbnails = postThumbnails;
    }

    @GetMapping("/api/search")
    public PageResponse<PostSummaryResponse> search(@CurrentBlog Blog blog,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        PageQuery pageQuery = PageQuery.of(page, size, SearchService.PAGE_SIZE);
        Page<Post> posts = searchService.search(blog, LoginMembers.currentId(), q, pageQuery);
        Map<Long, String> thumbnails = postThumbnails.of(posts.getContent());
        return PageResponse.from(posts, post -> PostSummaryResponse.of(post, blog, thumbnails.get(post.getId())));
    }

}
