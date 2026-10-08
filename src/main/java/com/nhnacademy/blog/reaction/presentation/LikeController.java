package com.nhnacademy.blog.reaction.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.reaction.application.LikeService;
import com.nhnacademy.blog.reaction.presentation.dto.LikeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공감 켜기·끄기 (T046, SOC-01). 블로그 주소에서 부른다. 이미 켜진 것을 켜거나 꺼진 것을 꺼도 200이다.
 */
@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PutMapping("/api/posts/{id}/like")
    public LikeResponse like(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                             @PathVariable Long id) {
        return LikeResponse.from(likeService.like(blog, id, member));
    }

    @DeleteMapping("/api/posts/{id}/like")
    public LikeResponse unlike(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                               @PathVariable Long id) {
        return LikeResponse.from(likeService.unlike(blog, id, member));
    }

}
