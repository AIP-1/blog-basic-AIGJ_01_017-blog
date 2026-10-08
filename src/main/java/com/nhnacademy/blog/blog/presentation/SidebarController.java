package com.nhnacademy.blog.blog.presentation;

import com.nhnacademy.blog.blog.application.SidebarService;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.presentation.dto.SidebarResponse;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이드바 (T025, BLOG-04). 한 블로그의 모든 화면이 같은 사이드바를 그린다.
 */
@RestController
public class SidebarController {

    private final SidebarService sidebarService;

    public SidebarController(SidebarService sidebarService) {
        this.sidebarService = sidebarService;
    }

    @GetMapping("/api/blog/sidebar")
    public SidebarResponse sidebar(@CurrentBlog Blog blog) {
        return SidebarResponse.from(sidebarService.sidebar(blog, LoginMembers.currentId()));
    }

}
