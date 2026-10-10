package com.nhnacademy.blog.search.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.BlogHostResolver;
import com.nhnacademy.blog.global.host.CurrentBlogArgumentResolver;
import com.nhnacademy.blog.global.host.RequestHost;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import com.nhnacademy.blog.search.application.BlogSearchService;
import com.nhnacademy.blog.search.application.SearchService;
import com.nhnacademy.blog.search.presentation.dto.BlogSearchResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색. 같은 주소(GET /api/search)가 요청 Host에 따라 두 가지다(contracts/rest-api.md SRCH).
 * <ul>
 *   <li>블로그 주소: 그 블로그 안 글 검색 (T047, SRCH-01). 블로그가 없거나 볼 수 없으면 404</li>
 *   <li>플랫폼 주소: 전체 검색 (T065, SRCH-02). type=post(기본)는 모든 블로그의 글, type=blog는 블로그</li>
 * </ul>
 * Spring은 Host로 메서드를 고를 수 없어서 한 메서드에서 나눈다. 블로그 주소의 판단은 @CurrentBlog와 같은 코드다.
 */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final BlogSearchService blogSearchService;
    private final PostThumbnails postThumbnails;
    private final BlogHostResolver blogHostResolver;
    private final CurrentBlogArgumentResolver currentBlog;

    public SearchController(SearchService searchService, BlogSearchService blogSearchService,
                            PostThumbnails postThumbnails, BlogHostResolver blogHostResolver,
                            CurrentBlogArgumentResolver currentBlog) {
        this.searchService = searchService;
        this.blogSearchService = blogSearchService;
        this.postThumbnails = postThumbnails;
        this.blogHostResolver = blogHostResolver;
        this.currentBlog = currentBlog;
    }

    /** 결과는 10개씩 페이지. 글이면 PostSummary, 블로그면 { blog, owner, subscriberCount }. 검색어가 없으면 400. */
    @GetMapping("/api/search")
    public PageResponse<?> search(HttpServletRequest request,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        PageQuery pageQuery = PageQuery.of(page, size, SearchService.PAGE_SIZE);
        Long viewerId = LoginMembers.currentId();
        if (!(blogHostResolver.resolve(request) instanceof RequestHost.Platform)) {
            Blog blog = currentBlog.resolve(request);
            return posts(searchService.search(blog, viewerId, q, pageQuery), blog);
        }
        if (type == null || type.isBlank() || type.equals("post")) {
            return posts(searchService.searchAll(viewerId, q, pageQuery), null);
        }
        if (type.equals("blog")) {
            return PageResponse.from(blogSearchService.search(viewerId, q, pageQuery), BlogSearchResponse::from);
        }
        throw BusinessException.invalidField("type", "post 또는 blog여야 합니다.");
    }

    /** blog가 있으면 블로그 안 검색이라 모든 글이 그 블로그다. 전체 검색은 글마다 함께 읽은 블로그를 쓴다. */
    private PageResponse<PostSummaryResponse> posts(Page<Post> posts, Blog blog) {
        Map<Long, String> thumbnails = postThumbnails.of(posts.getContent());
        return PageResponse.from(posts, post -> PostSummaryResponse.of(post, blog == null ? post.getBlog() : blog,
                thumbnails.get(post.getId())));
    }

}
