package com.nhnacademy.blog.post.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.BlogHostResolver;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.Idempotent;
import com.nhnacademy.blog.global.web.RequestValidator;
import com.nhnacademy.blog.post.application.PostService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.ManagedPostResponse;
import com.nhnacademy.blog.post.presentation.dto.PostSaveRequest;
import com.nhnacademy.blog.post.presentation.dto.PostSavedResponse;
import com.nhnacademy.blog.post.presentation.dto.VisibilityRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 주인의 글 쓰기 (T031~T034, POST-01·02·03·06). 블로그 주소에서 부른다.
 * 상태 코드 순서: 글이 없거나 볼 수 없음 404 → 비회원 401 → 주인 아님 403 → 숨긴 글 403 → 입력 400.
 */
@RestController
public class PostManageController {

    private final PostService postService;
    private final BlogOwnerGuard blogOwnerGuard;
    private final RequestValidator requestValidator;
    private final BlogHostResolver blogHostResolver;

    public PostManageController(PostService postService, BlogOwnerGuard blogOwnerGuard,
                                RequestValidator requestValidator, BlogHostResolver blogHostResolver) {
        this.postService = postService;
        this.blogOwnerGuard = blogOwnerGuard;
        this.requestValidator = requestValidator;
        this.blogHostResolver = blogHostResolver;
    }

    /** 발행 (T031). Idempotency-Key가 필수이고, 같은 키로 두 번 오면 처음 응답을 그대로 준다(SC-004). */
    @Idempotent
    @PostMapping("/api/posts")
    public ResponseEntity<PostSavedResponse> publish(@CurrentBlog Blog blog,
                                                     @AuthenticationPrincipal LoginMember member,
                                                     @RequestBody PostSaveRequest request,
                                                     HttpServletRequest httpRequest) {
        blogOwnerGuard.requireOwner(blog, member);
        requestValidator.validate(request).checkSupported();
        Post post = postService.publish(blog, request.toCommand());
        String url = postUrl(httpRequest, blog, post);
        return ResponseEntity.created(URI.create(url)).body(PostSavedResponse.of(post, url));
    }

    /** 편집용 글 (수정 화면). 숨긴 글이면 사유가 함께 간다. */
    @GetMapping("/api/manage/posts/{id}")
    public ManagedPostResponse managedPost(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                           @PathVariable Long id) {
        Post post = postService.findOwned(blog, id, member);
        return ManagedPostResponse.of(post, postService.blindReason(post));
    }

    /** 수정 (T032). 주소·처음 발행 시각·목록 순서는 그대로다. */
    @PutMapping("/api/posts/{id}")
    public PostSavedResponse edit(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                  @PathVariable Long id, @RequestBody PostSaveRequest request,
                                  HttpServletRequest httpRequest) {
        postService.findEditable(blog, id, member);
        requestValidator.validate(request).checkSupported();
        Post post = postService.edit(blog, id, member, request.toCommand());
        return PostSavedResponse.of(post, postUrl(httpRequest, blog, post));
    }

    /** 공개 범위 변경 (T034). */
    @PatchMapping("/api/posts/{id}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeVisibility(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                 @PathVariable Long id, @RequestBody VisibilityRequest request) {
        postService.findOwned(blog, id, member);
        requestValidator.validate(request).checkSupported();
        postService.changeVisibility(blog, id, member, request.visibility());
    }

    /** 삭제 (T033). 소프트 삭제, 댓글·공감·알림도 함께. */
    @DeleteMapping("/api/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id) {
        postService.delete(blog, id, member);
    }

    private String postUrl(HttpServletRequest request, Blog blog, Post post) {
        return blogHostResolver.blogUrl(request, blog, "/" + post.getId());
    }

}
