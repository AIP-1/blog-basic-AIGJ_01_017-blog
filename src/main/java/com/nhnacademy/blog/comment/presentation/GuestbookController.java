package com.nhnacademy.blog.comment.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.application.GuestbookService;
import com.nhnacademy.blog.comment.presentation.dto.CommentEditRequest;
import com.nhnacademy.blog.comment.presentation.dto.CommentResponse;
import com.nhnacademy.blog.comment.presentation.dto.GuestbookRequest;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.Idempotent;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.global.web.RequestValidator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방명록 (T066, CMT-04). 블로그 주소에서 부른다. 응답은 댓글과 같은 Comment 모양이다.
 */
@RestController
public class GuestbookController {

    private final GuestbookService guestbookService;
    private final RequestValidator requestValidator;

    public GuestbookController(GuestbookService guestbookService, RequestValidator requestValidator) {
        this.guestbookService = guestbookService;
        this.requestValidator = requestValidator;
    }

    /** 누구나. 20개씩 최신순, 답글은 부모 안에. 비밀글은 주인·작성자가 아니면 내용 없이 SECRET. */
    @GetMapping("/api/guestbook")
    public PageResponse<CommentResponse> list(@CurrentBlog Blog blog, @RequestParam(required = false) Integer page) {
        return PageResponse.from(guestbookService.list(blog, LoginMembers.currentId(),
                PageQuery.of(page, null, GuestbookService.PAGE_SIZE)), CommentResponse::from);
    }

    /** 회원만. 댓글과 같이 Idempotency-Key가 필수이고, 같은 키로 두 번 오면 처음 응답을 준다. */
    @Idempotent
    @PostMapping("/api/guestbook")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse write(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                 @RequestBody GuestbookRequest request) {
        guestbookService.requireMember(member);
        requestValidator.validate(request);
        return CommentResponse.from(
                guestbookService.write(blog, member, request.content(), request.parentId(), request.isSecret()));
    }

    /** 작성자 본인만. */
    @PatchMapping("/api/guestbook/{id}")
    public CommentResponse edit(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                @PathVariable Long id, @RequestBody CommentEditRequest request) {
        guestbookService.checkEditable(blog, id, member);
        requestValidator.validate(request);
        return CommentResponse.from(guestbookService.edit(blog, id, member, request.content()));
    }

    /** 작성자 본인과 블로그 주인만. */
    @DeleteMapping("/api/guestbook/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id) {
        guestbookService.delete(blog, id, member);
    }

}
