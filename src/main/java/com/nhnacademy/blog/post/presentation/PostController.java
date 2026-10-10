package com.nhnacademy.blog.post.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.global.web.VisitorKeys;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.application.PostQueryService;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.application.ViewService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostDetailResponse;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import com.nhnacademy.blog.reaction.application.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 글 목록 (T024, BLOG-03, 카테고리별 CAT-02, 태그별 T039 TAG-02)과 글 상세 (T041, POST-04), 조회 기록 (T053, POST-09).
 * 블로그 주소에서 부른다.
 */
@RestController
public class PostController {

    private final PostQueryService postQueryService;
    private final PostReadService postReadService;
    private final PostThumbnails postThumbnails;
    private final LikeService likeService;
    private final ViewService viewService;
    private final VisitorKeys visitorKeys;

    public PostController(PostQueryService postQueryService, PostReadService postReadService,
                          PostThumbnails postThumbnails, LikeService likeService, ViewService viewService,
                          VisitorKeys visitorKeys) {
        this.postQueryService = postQueryService;
        this.postReadService = postReadService;
        this.postThumbnails = postThumbnails;
        this.likeService = likeService;
        this.viewService = viewService;
        this.visitorKeys = visitorKeys;
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
    /**
     * 같은 카테고리의 다른 글 (T104, OWN-05). size는 1~10, 없으면 5. 글을 볼 수 없으면 글 상세와 같이 404·403.
     */
    @GetMapping("/api/posts/{id}/same-category")
    public List<PostSummaryResponse> sameCategory(@CurrentBlog Blog blog, @PathVariable Long id,
                                                  @RequestParam(required = false) Integer size) {
        int count = size == null ? 5 : size;
        if (count < 1 || count > 10) {
            throw BusinessException.invalidField("size", "1~10개까지 볼 수 있습니다.");
        }
        Long viewerId = LoginMembers.currentId();
        Post post = postReadService.readable(blog, id, viewerId);
        List<Post> posts = postQueryService.sameCategory(blog, post, viewerId, count);
        Map<Long, String> thumbnails = postThumbnails.of(posts);
        return posts.stream().map(found -> PostSummaryResponse.of(found, blog, thumbnails.get(found.getId()))).toList();
    }

    @GetMapping("/api/posts/{id}")
    public PostDetailResponse post(@CurrentBlog Blog blog, @PathVariable Long id) {
        Long viewerId = LoginMembers.currentId();
        return PostDetailResponse.from(postReadService.detail(blog, id, viewerId), likeService.likes(id, viewerId));
    }

    /**
     * 글을 본 기록. 화면이 본문을 보여 준 뒤 부른다. 같은 조회자가 5분 안에 다시 부르면 세지 않지만 응답은 같다(204).
     * 볼 수 없는 글이면 상세와 같이 404·403이다. 가시성 확인과 기록은 다른 트랜잭션이다(ViewService.record 설명).
     */
    @PostMapping("/api/posts/{id}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@CurrentBlog Blog blog, @PathVariable Long id, HttpServletRequest request,
                     HttpServletResponse response) {
        postReadService.readable(blog, id, LoginMembers.currentId());
        viewService.record(id, visitorKeys.resolve(request, response));
    }

}
