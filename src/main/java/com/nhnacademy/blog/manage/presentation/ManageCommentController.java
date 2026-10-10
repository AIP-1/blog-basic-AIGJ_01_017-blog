package com.nhnacademy.blog.manage.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.manage.application.ManageCommentService;
import com.nhnacademy.blog.manage.presentation.dto.ReceivedCommentResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 받은 댓글·방명록 (T068, MNG-02). 블로그 주소에서 부르고, 블로그 주인만 쓴다.
 * 지우기는 글 상세·방명록과 같은 DELETE /api/comments/{id}, /api/guestbook/{id}이고,
 * 답글은 POST /api/posts/{id}/comments, /api/guestbook에 parentId를 붙여 쓴다(따로 API를 두지 않음).
 */
@RestController
public class ManageCommentController {

    private final ManageCommentService manageCommentService;
    private final BlogOwnerGuard blogOwnerGuard;

    public ManageCommentController(ManageCommentService manageCommentService, BlogOwnerGuard blogOwnerGuard) {
        this.manageCommentService = manageCommentService;
        this.blogOwnerGuard = blogOwnerGuard;
    }

    /** type은 comment(기본)·guestbook. 비회원 401 → 주인 아님 403 → 알 수 없는 type·page 400 순서다. */
    @GetMapping("/api/manage/comments")
    public PageResponse<ReceivedCommentResponse> comments(@CurrentBlog Blog blog,
                                                          @AuthenticationPrincipal LoginMember member,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) Integer page) {
        blogOwnerGuard.requireOwner(blog, member);
        PageQuery query = PageQuery.of(page, null, ManageCommentService.PAGE_SIZE);
        if (type == null || type.isBlank() || type.equals("comment")) {
            return PageResponse.from(manageCommentService.comments(blog, query), ReceivedCommentResponse::from);
        }
        if (type.equals("guestbook")) {
            return PageResponse.from(manageCommentService.guestbook(blog, query), ReceivedCommentResponse::from);
        }
        throw BusinessException.invalidField("type", "comment 또는 guestbook이어야 합니다.");
    }

}
