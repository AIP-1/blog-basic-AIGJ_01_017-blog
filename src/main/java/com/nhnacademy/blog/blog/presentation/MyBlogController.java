package com.nhnacademy.blog.blog.presentation;

import com.nhnacademy.blog.blog.application.MyBlogService;
import com.nhnacademy.blog.blog.presentation.dto.MyBlogResponse;
import com.nhnacademy.blog.blog.presentation.dto.PrimaryBlogRequest;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.web.RequestValidator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지의 내 블로그 (BLOG-08). 주소는 /api/me 아래지만 블로그를 다루므로 blog 기능에 둔다.
 * 상태 코드 순서: 비회원 401 → 입력 400.
 */
@RestController
public class MyBlogController {

    private final MyBlogService myBlogService;
    private final RequestValidator requestValidator;

    public MyBlogController(MyBlogService myBlogService, RequestValidator requestValidator) {
        this.myBlogService = myBlogService;
        this.requestValidator = requestValidator;
    }

    /** 내 활성 블로그, 만든 순서. */
    @GetMapping("/api/me/blogs")
    @PreAuthorize("isAuthenticated()")
    public List<MyBlogResponse> myBlogs(@AuthenticationPrincipal LoginMember member) {
        return myBlogService.myBlogs(member.id()).stream().map(MyBlogResponse::from).toList();
    }

    /** 대표 블로그 바꾸기. 내 활성 블로그만(아니면 400 fieldErrors blogId). */
    @PutMapping("/api/me/primary-blog")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePrimary(@AuthenticationPrincipal LoginMember member, @RequestBody PrimaryBlogRequest request) {
        requestValidator.validate(request);
        myBlogService.changePrimary(member.id(), request.blogId());
    }

}
