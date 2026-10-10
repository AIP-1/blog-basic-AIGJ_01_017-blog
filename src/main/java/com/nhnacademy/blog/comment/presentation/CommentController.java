package com.nhnacademy.blog.comment.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.application.CommentPage;
import com.nhnacademy.blog.comment.application.CommentService;
import com.nhnacademy.blog.comment.application.CommentView;
import com.nhnacademy.blog.comment.presentation.dto.CommentEditRequest;
import com.nhnacademy.blog.comment.presentation.dto.CommentListResponse;
import com.nhnacademy.blog.comment.presentation.dto.CommentRequest;
import com.nhnacademy.blog.comment.presentation.dto.CommentResponse;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.CursorResponse;
import com.nhnacademy.blog.global.web.Idempotent;
import com.nhnacademy.blog.global.web.RequestValidator;
import com.nhnacademy.blog.global.web.TimeIdCursor;
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
 * 댓글 (T044, CMT-01, CMT-02, CMT-03). 블로그 주소에서 부른다.
 */
@RestController
public class CommentController {

    private final CommentService commentService;
    private final RequestValidator requestValidator;

    public CommentController(CommentService commentService, RequestValidator requestValidator) {
        this.commentService = commentService;
        this.requestValidator = requestValidator;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public CommentListResponse comments(@CurrentBlog Blog blog, @PathVariable Long postId,
                                        @RequestParam(required = false) String cursor) {
        CommentPage page = commentService.list(blog, postId, LoginMembers.currentId(), TimeIdCursor.decode(cursor));
        CursorResponse<CommentView> slice = CursorResponse.of(page.fetched(), CommentService.PAGE_SIZE,
                last -> new TimeIdCursor(last.entry().getCreatedAt(), last.entry().getId()).encode());
        return new CommentListResponse(slice.content().stream().map(CommentResponse::from).toList(),
                slice.nextCursor(), page.totalCount());
    }

    /** 회원만. Idempotency-Key가 필수이고 같은 키로 두 번 오면 처음 응답을 준다(연타해도 댓글 하나). */
    @Idempotent
    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse write(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                 @PathVariable Long postId, @RequestBody CommentRequest request) {
        commentService.writablePost(blog, postId, member);
        requestValidator.validate(request);
        return CommentResponse.from(commentService.write(blog, postId, member, request.content(), request.parentId()));
    }

    /** 작성자 본인만(CMT-03). 숨긴 댓글은 403. 비회원 401, 남의 댓글 403, 그다음 입력 오류 400 순서다. */
    @PatchMapping("/api/comments/{id}")
    public CommentResponse edit(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                @PathVariable Long id, @RequestBody CommentEditRequest request) {
        commentService.checkEditable(blog, id, member);
        requestValidator.validate(request);
        return CommentResponse.from(commentService.edit(blog, id, member, request.content()));
    }

    /** 작성자 본인과 블로그 주인만. */
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id) {
        commentService.delete(blog, id, member);
    }

}
