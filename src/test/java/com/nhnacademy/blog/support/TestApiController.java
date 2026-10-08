package com.nhnacademy.blog.support;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.CurrentBlog;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 전용 API. 실제 기능 API가 생기기 전에 인증·권한·연타 방지 장치를 확인한다.
 */
@RestController
public class TestApiController {

    private final BlogOwnerGuard blogOwnerGuard;

    public TestApiController(BlogOwnerGuard blogOwnerGuard) {
        this.blogOwnerGuard = blogOwnerGuard;
    }

    @GetMapping("/api/test/blog")
    public Map<String, String> blog(@CurrentBlog Blog blog) {
        return Map.of("address", blog.getAddress());
    }

    @PutMapping("/api/test/blog/settings")
    public Map<String, String> ownerOnly(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member) {
        blogOwnerGuard.requireOwner(blog, member);
        return Map.of("result", "ok");
    }

    @GetMapping("/api/test/me")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> me(@AuthenticationPrincipal LoginMember member) {
        return Map.of("id", member.id(), "role", member.role().name());
    }

    @GetMapping("/api/test/public")
    public Map<String, Object> publicApi(@AuthenticationPrincipal LoginMember member) {
        return Map.of("loggedIn", member != null);
    }

    @PostMapping("/api/test/echo")
    public Map<String, String> echo() {
        return Map.of("result", "ok");
    }

    @GetMapping("/api/admin/test")
    public Map<String, String> admin() {
        return Map.of("result", "admin");
    }

}
