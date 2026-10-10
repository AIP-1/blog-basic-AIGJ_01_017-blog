package com.nhnacademy.blog.tag.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.tag.application.TagListService;
import com.nhnacademy.blog.tag.application.TagManageService;
import com.nhnacademy.blog.tag.presentation.dto.ManagedTagResponse;
import com.nhnacademy.blog.tag.presentation.dto.TagCountResponse;
import com.nhnacademy.blog.tag.presentation.dto.TagRenameRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 태그 목록 (T054, TAG-03)과 이름 바꾸기·지우기 (T098, TAG-04). 블로그 주소에서 부른다.
 * 바꾸는 API는 주인만이고, 상태 코드 순서는 카테고리와 같다(없음 404 → 비회원 401 → 남 403 → 입력 400 → 중복 409).
 */
@RestController
public class TagController {

    private final TagListService tagListService;
    private final TagManageService tagManageService;
    private final BlogOwnerGuard blogOwnerGuard;

    public TagController(TagListService tagListService, TagManageService tagManageService,
                         BlogOwnerGuard blogOwnerGuard) {
        this.tagListService = tagListService;
        this.tagManageService = tagManageService;
        this.blogOwnerGuard = blogOwnerGuard;
    }

    @GetMapping("/api/tags")
    public List<TagCountResponse> tags(@CurrentBlog Blog blog) {
        return tagListService.tags(blog, LoginMembers.currentId()).stream().map(TagCountResponse::from).toList();
    }

    /** 관리 화면의 태그 표. 임시저장·예약 글에만 단 태그도 나온다(주인만). */
    @GetMapping("/api/manage/tags")
    public List<ManagedTagResponse> managed(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member) {
        blogOwnerGuard.requireOwner(blog, member);
        return tagListService.managed(blog, member.id()).stream().map(ManagedTagResponse::from).toList();
    }

    @PatchMapping("/api/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id,
                       @RequestBody TagRenameRequest request) {
        tagManageService.find(blog, id);
        blogOwnerGuard.requireOwner(blog, member);
        tagManageService.rename(blog, id, request.name());
    }

    @DeleteMapping("/api/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id) {
        tagManageService.find(blog, id);
        blogOwnerGuard.requireOwner(blog, member);
        tagManageService.delete(blog, id);
    }

}
