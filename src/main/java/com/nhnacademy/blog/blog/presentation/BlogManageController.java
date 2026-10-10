package com.nhnacademy.blog.blog.presentation;

import com.nhnacademy.blog.blog.application.BlogDeletionService;
import com.nhnacademy.blog.blog.application.BlogMoveService;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.presentation.dto.BlogDeleteRequest;
import com.nhnacademy.blog.blog.presentation.dto.MovePostsRequest;
import com.nhnacademy.blog.blog.presentation.dto.MoveTargetRequest;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.CurrentBlog;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 이사(T106, BLOG-06)와 삭제(T107, BLOG-07). 모두 블로그 주소에서 주인만 부른다.
 * 이사한 블로그도 주인은 관리 API를 계속 쓴다(CurrentBlog가 주인에게는 이사한 블로그를 돌려준다).
 */
@RestController
public class BlogManageController {

    private final BlogMoveService blogMoveService;
    private final BlogDeletionService blogDeletionService;
    private final BlogOwnerGuard blogOwnerGuard;

    public BlogManageController(BlogMoveService blogMoveService, BlogDeletionService blogDeletionService,
                                BlogOwnerGuard blogOwnerGuard) {
        this.blogMoveService = blogMoveService;
        this.blogDeletionService = blogDeletionService;
        this.blogOwnerGuard = blogOwnerGuard;
    }

    /** `{ movedCount }`. 대상이 자기 블로그·지운 블로그·남의 블로그면 400 INVALID_MOVE_TARGET. */
    @PostMapping("/api/blog/move-posts")
    public Map<String, Integer> movePosts(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                          @RequestBody MovePostsRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        return Map.of("movedCount", blogMoveService.movePosts(blog, request.postIds(), request.targetBlogId()));
    }

    @PutMapping("/api/blog/moved-to")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveTo(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                       @RequestBody MoveTargetRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        blogMoveService.moveTo(blog, request.targetBlogId());
    }

    @DeleteMapping("/api/blog/moved-to")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMove(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member) {
        blogOwnerGuard.requireOwner(blog, member);
        blogMoveService.cancelMove(blog);
    }

    @GetMapping("/api/blog/deletion-preview")
    public BlogDeletionService.Preview deletionPreview(@CurrentBlog Blog blog,
                                                       @AuthenticationPrincipal LoginMember member) {
        blogOwnerGuard.requireOwner(blog, member);
        return blogDeletionService.preview(blog);
    }

    @DeleteMapping("/api/blog")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                       @RequestBody BlogDeleteRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        blogDeletionService.delete(blog, request.confirmAddress());
    }

}
