package com.nhnacademy.blog.bookmark.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.bookmark.application.BookmarkService;
import com.nhnacademy.blog.bookmark.presentation.dto.BookmarkResponse;
import com.nhnacademy.blog.bookmark.presentation.dto.SavedPostResponse;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.CursorResponse;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.image.application.PostThumbnails;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 저장 (T074·T075, SOC-03). 글 상세에서 저장·취소(블로그 주소), 마이페이지 "저장한 글"과 바로 취소(플랫폼 주소). */
@RestController
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final PostThumbnails postThumbnails;

    public BookmarkController(BookmarkService bookmarkService, PostThumbnails postThumbnails) {
        this.bookmarkService = bookmarkService;
        this.postThumbnails = postThumbnails;
    }

    @PutMapping("/api/posts/{id}/bookmark")
    public BookmarkResponse save(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                 @PathVariable Long id) {
        bookmarkService.save(blog, id, member);
        return new BookmarkResponse(true);
    }

    @DeleteMapping("/api/posts/{id}/bookmark")
    public BookmarkResponse unsave(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                   @PathVariable Long id) {
        bookmarkService.unsave(blog, id, member);
        return new BookmarkResponse(false);
    }

    @GetMapping("/api/me/bookmarks")
    @PreAuthorize("isAuthenticated()")
    public CursorResponse<SavedPostResponse> list(@AuthenticationPrincipal LoginMember member,
                                                  @RequestParam(required = false) String cursor) {
        List<BookmarkService.SavedItem> items = bookmarkService.list(member.id(), TimeIdCursor.decode(cursor));
        Map<Long, String> thumbnails = postThumbnails.of(items.stream().map(BookmarkService.SavedItem::post)
                .filter(Objects::nonNull).toList());
        List<SavedPostResponse> responses = items.stream()
                .map(item -> SavedPostResponse.from(item, item.post() == null ? null : thumbnails.get(item.post().getId())))
                .toList();
        // 다음 커서는 저장 시각·저장 번호로(글이 아니라 저장 행 기준)
        List<BookmarkService.SavedItem> page = items.size() > BookmarkService.PAGE_SIZE
                ? items.subList(0, BookmarkService.PAGE_SIZE) : items;
        String next = items.size() > BookmarkService.PAGE_SIZE
                ? new TimeIdCursor(page.getLast().bookmark().getCreatedAt(), page.getLast().bookmark().getId()).encode()
                : null;
        return new CursorResponse<>(responses.subList(0, page.size()), next);
    }

    @DeleteMapping("/api/me/bookmarks/{postId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal LoginMember member, @PathVariable Long postId) {
        bookmarkService.remove(member.id(), postId);
    }

}
