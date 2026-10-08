package com.nhnacademy.blog.blog.presentation;

import com.nhnacademy.blog.blog.application.BlogQueryService;
import com.nhnacademy.blog.blog.application.BlogService;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.presentation.dto.AddressAvailabilityResponse;
import com.nhnacademy.blog.blog.presentation.dto.BlogCreateRequest;
import com.nhnacademy.blog.blog.presentation.dto.BlogResponse;
import com.nhnacademy.blog.global.auth.LoginMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 개설 (T022, BLOG-01). 플랫폼 주소에서 부른다.
 */
@RestController
public class BlogController {

    private final BlogService blogService;
    private final BlogQueryService blogQueryService;

    public BlogController(BlogService blogService, BlogQueryService blogQueryService) {
        this.blogService = blogService;
        this.blogQueryService = blogQueryService;
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

}
