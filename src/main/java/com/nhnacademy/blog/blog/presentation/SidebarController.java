package com.nhnacademy.blog.blog.presentation;

import com.nhnacademy.blog.blog.application.SidebarModules;
import com.nhnacademy.blog.blog.application.SidebarService;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.presentation.dto.SidebarModuleItem;
import com.nhnacademy.blog.blog.presentation.dto.SidebarResponse;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이드바 (T025, BLOG-04). 한 블로그의 모든 화면이 같은 사이드바를 그린다.
 */
@RestController
public class SidebarController {

    private final SidebarService sidebarService;
    private final SidebarModules sidebarModules;
    private final BlogOwnerGuard blogOwnerGuard;

    public SidebarController(SidebarService sidebarService, SidebarModules sidebarModules,
                             BlogOwnerGuard blogOwnerGuard) {
        this.sidebarService = sidebarService;
        this.sidebarModules = sidebarModules;
        this.blogOwnerGuard = blogOwnerGuard;
    }

    @GetMapping("/api/blog/sidebar")
    public SidebarResponse sidebar(@CurrentBlog Blog blog) {
        return SidebarResponse.from(sidebarService.sidebar(blog, LoginMembers.currentId()));
    }

    /** 모듈 8개 전부(숨긴 것 포함) 주인이 정한 순서로 (BLOG-05). 주인만. */
    @GetMapping("/api/blog/sidebar/modules")
    public List<SidebarModuleItem> modules(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member) {
        blogOwnerGuard.requireOwner(blog, member);
        return sidebarModules.of(blog.getId()).stream()
                .map(slot -> new SidebarModuleItem(slot.type().name(), slot.visible()))
                .toList();
    }

    /** 8개를 원하는 순서로 전체 교체. 8종이 한 번씩이 아니거나 PROFILE을 숨기면 400(modules). */
    @PutMapping("/api/blog/sidebar/modules")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replace(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                        @RequestBody List<SidebarModuleItem> request) {
        blogOwnerGuard.requireOwner(blog, member);
        sidebarModules.replace(blog.getId(), request.stream()
                .map(item -> new SidebarModules.Slot(SidebarModules.parse(item.moduleType()),
                        Boolean.TRUE.equals(item.isVisible())))
                .toList());
    }

}
