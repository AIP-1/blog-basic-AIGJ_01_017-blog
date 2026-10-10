package com.nhnacademy.blog.blog.presentation;

import com.nhnacademy.blog.blog.application.BlogQueryService;
import com.nhnacademy.blog.blog.application.BlogService;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.presentation.dto.AddressAvailabilityResponse;
import com.nhnacademy.blog.blog.presentation.dto.BlogCreateRequest;
import com.nhnacademy.blog.blog.presentation.dto.BlogResponse;
import com.nhnacademy.blog.blog.presentation.dto.BlogUpdateRequest;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.RequestValidator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 개설(T022, BLOG-01, 플랫폼 주소)과 블로그 정보 조회·수정(T023, BLOG-02·03, 블로그 주소).
 */
@RestController
public class BlogController {

    private final BlogService blogService;
    private final BlogQueryService blogQueryService;
    private final BlogOwnerGuard blogOwnerGuard;
    private final RequestValidator requestValidator;

    public BlogController(BlogService blogService, BlogQueryService blogQueryService, BlogOwnerGuard blogOwnerGuard,
                          RequestValidator requestValidator) {
        this.blogService = blogService;
        this.blogQueryService = blogQueryService;
        this.blogOwnerGuard = blogOwnerGuard;
        this.requestValidator = requestValidator;
    }

    @GetMapping("/api/blogs/address-availability")
    @PreAuthorize("isAuthenticated()")
    public AddressAvailabilityResponse addressAvailability(@RequestParam String address) {
        return AddressAvailabilityResponse.from(blogService.checkAddress(address));
    }

    /** 201 Blog. 6번째는 409 BLOG_LIMIT_EXCEEDED, 처음 만든 블로그는 대표 블로그다. */
    @PostMapping("/api/blogs")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public BlogResponse create(@Valid @RequestBody BlogCreateRequest request,
                               @AuthenticationPrincipal LoginMember member) {
        Blog blog = blogService.open(member.id(), request.address(), request.name(), request.description());
        return BlogResponse.from(blogQueryService.detail(blog, member.id()));
    }

    /** 요청 Host의 블로그. 없거나 볼 수 없으면 404(@CurrentBlog). */
    @GetMapping("/api/blog")
    public BlogResponse blog(@CurrentBlog Blog blog) {
        return BlogResponse.from(blogQueryService.detail(blog, LoginMembers.currentId()));
    }

    /** 주인만. 비회원 401, 남의 블로그 403, 그다음 입력 오류 400 순서다. */
    @PatchMapping("/api/blog")
    public BlogResponse update(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                               @RequestBody BlogUpdateRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        requestValidator.validate(request);
        Blog updated = blogService.updateInfo(blog.getAddress(), member.id(), request.name(), request.description(),
                request.profileImageId());
        return BlogResponse.from(blogQueryService.detail(updated, member.id()));
    }

}
