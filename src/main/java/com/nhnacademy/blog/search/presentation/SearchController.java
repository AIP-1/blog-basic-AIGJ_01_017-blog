package com.nhnacademy.blog.search.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.BlogAddressResolver;
import com.nhnacademy.blog.global.host.CurrentBlogArgumentResolver;
import com.nhnacademy.blog.global.host.RequestBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import com.nhnacademy.blog.search.application.BlogSearchService;
import com.nhnacademy.blog.search.application.SearchService;
import com.nhnacademy.blog.search.application.SemanticHit;
import com.nhnacademy.blog.search.application.SemanticSearchService;
import com.nhnacademy.blog.search.presentation.dto.BlogSearchResponse;
import com.nhnacademy.blog.search.presentation.dto.SemanticSearchResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색. 같은 주소(GET /api/search)가 X-Blog-Address 헤더가 있는지에 따라 두 가지다(contracts/rest-api.md SRCH).
 * <ul>
 *   <li>블로그 주소: 그 블로그 안 글 검색 (T047, SRCH-01). 블로그가 없거나 볼 수 없으면 404</li>
 *   <li>플랫폼 주소: 전체 검색 (T065, SRCH-02). type=post(기본)는 모든 블로그의 글, type=blog는 블로그</li>
 * </ul>
 * mode=semantic이면 글을 낱말 대신 뜻으로 찾는다(의미 검색, T121, OWN-07). 블로그 검색(type=blog)에는 없다.
 * 한 메서드에서 헤더로 나눈다. 블로그 주소의 판단은 @CurrentBlog와 같은 코드다.
 */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final SemanticSearchService semanticSearchService;
    private final BlogSearchService blogSearchService;
    private final PostThumbnails postThumbnails;
    private final BlogAddressResolver blogAddressResolver;
    private final CurrentBlogArgumentResolver currentBlog;

    public SearchController(SearchService searchService, SemanticSearchService semanticSearchService,
                            BlogSearchService blogSearchService, PostThumbnails postThumbnails,
                            BlogAddressResolver blogAddressResolver, CurrentBlogArgumentResolver currentBlog) {
        this.searchService = searchService;
        this.semanticSearchService = semanticSearchService;
        this.blogSearchService = blogSearchService;
        this.postThumbnails = postThumbnails;
        this.blogAddressResolver = blogAddressResolver;
        this.currentBlog = currentBlog;
    }

    /**
     * 결과는 10개씩 페이지. 글이면 PostSummary, 의미 검색이면 { post, similarity }, 블로그면 { blog, owner, subscriberCount }.
     * 검색어가 없으면 400.
     */
    @GetMapping("/api/search")
    public PageResponse<?> search(HttpServletRequest request,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String mode,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        boolean semantic = semantic(mode);
        PageQuery pageQuery = PageQuery.of(page, size, SearchService.PAGE_SIZE);
        Long viewerId = LoginMembers.currentId();
        if (!(blogAddressResolver.resolve(request) instanceof RequestBlog.Platform)) {
            Blog blog = currentBlog.resolve(request);
            if (semantic) {
                return hits(semanticSearchService.search(blog, viewerId, q, pageQuery), blog);
            }
            return posts(searchService.search(blog, viewerId, q, pageQuery), blog);
        }
        if (type == null || type.isBlank() || type.equals("post")) {
            if (semantic) {
                return hits(semanticSearchService.searchAll(viewerId, q, pageQuery), null);
            }
            return posts(searchService.searchAll(viewerId, q, pageQuery), null);
        }
        if (type.equals("blog")) {
            if (semantic) {
                throw BusinessException.invalidField("mode", "블로그 검색은 글자로만 찾습니다.");
            }
            return PageResponse.from(blogSearchService.search(viewerId, q, pageQuery), BlogSearchResponse::from);
        }
        throw BusinessException.invalidField("type", "post 또는 blog여야 합니다.");
    }

    /** mode를 빼면 글자 검색(keyword). */
    private static boolean semantic(String mode) {
        if (mode == null || mode.isBlank() || mode.equals("keyword")) {
            return false;
        }
        if (mode.equals("semantic")) {
            return true;
        }
        throw BusinessException.invalidField("mode", "keyword 또는 semantic이어야 합니다.");
    }

    private PageResponse<SemanticSearchResponse> hits(Page<SemanticHit> hits, Blog blog) {
        List<Post> posts = hits.getContent().stream().map(SemanticHit::post).toList();
        Map<Long, String> thumbnails = postThumbnails.of(posts);
        return PageResponse.from(hits, hit -> SemanticSearchResponse.of(
                PostSummaryResponse.of(hit.post(), blog == null ? hit.post().getBlog() : blog,
                        thumbnails.get(hit.post().getId())),
                hit.similarity()));
    }

    /** blog가 있으면 블로그 안 검색이라 모든 글이 그 블로그다. 전체 검색은 글마다 함께 읽은 블로그를 쓴다. */
    private PageResponse<PostSummaryResponse> posts(Page<Post> posts, Blog blog) {
        Map<Long, String> thumbnails = postThumbnails.of(posts.getContent());
        return PageResponse.from(posts, post -> PostSummaryResponse.of(post, blog == null ? post.getBlog() : blog,
                thumbnails.get(post.getId())));
    }

}
