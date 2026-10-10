package com.nhnacademy.blog.category.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.application.CategoryService;
import com.nhnacademy.blog.category.application.CategoryTreeService;
import com.nhnacademy.blog.category.presentation.dto.CategoryCreatedResponse;
import com.nhnacademy.blog.category.presentation.dto.CategoryOrderItem;
import com.nhnacademy.blog.category.presentation.dto.CategoryRequest;
import com.nhnacademy.blog.category.presentation.dto.CategoryTreeResponse;
import com.nhnacademy.blog.category.presentation.dto.CategoryUpdateRequest;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.RequestValidator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카테고리 (T029, CAT-01). 블로그 주소에서 부른다. 바꾸는 API는 주인만이고,
 * 상태 코드 순서(404 → 401 → 403 → 400 → 409)대로 대상 확인, 주인 검사, 입력 검증, 중복 순서로 본다.
 */
@RestController
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryTreeService categoryTreeService;
    private final BlogOwnerGuard blogOwnerGuard;
    private final RequestValidator requestValidator;

    public CategoryController(CategoryService categoryService, CategoryTreeService categoryTreeService,
                              BlogOwnerGuard blogOwnerGuard, RequestValidator requestValidator) {
        this.categoryService = categoryService;
        this.categoryTreeService = categoryTreeService;
        this.blogOwnerGuard = blogOwnerGuard;
        this.requestValidator = requestValidator;
    }

    /** 카테고리 트리와 볼 수 있는 글 수. 사이드바의 CATEGORY 모듈과 같다. */
    @GetMapping("/api/categories")
    public CategoryTreeResponse categories(@CurrentBlog Blog blog) {
        return CategoryTreeResponse.from(categoryTreeService.tree(blog, LoginMembers.currentId()));
    }

    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryCreatedResponse create(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                          @RequestBody CategoryRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        requestValidator.validate(request);
        return CategoryCreatedResponse.from(categoryService.create(blog, request.name(), request.parentId()));
    }

    /** `{ name }`이면 이름 변경, `{ isPrivate }`이면 비공개 켜기·끄기(CAT-05). 둘 다 보내도 된다. */
    @PatchMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id,
                       @RequestBody CategoryUpdateRequest request) {
        categoryService.find(blog, id);
        blogOwnerGuard.requireOwner(blog, member);
        requestValidator.validate(request);
        if (request.parentId() != null) {
            throw BusinessException.invalidField("parentId", "상위 카테고리는 순서 바꾸기에서 바꿉니다.");
        }
        if (request.name() == null && request.isPrivate() == null) {
            throw BusinessException.invalidField("name", "바꿀 이름이나 비공개 여부를 보내 주세요.");
        }
        if (request.name() != null) {
            categoryService.rename(blog, id, request.name());
        }
        if (request.isPrivate() != null) {
            categoryService.changePrivate(blog, id, request.isPrivate());
        }
    }

    /** 끌어서 놓은 결과 전체 `[{ id, parentId, sortOrder }]` (CAT-04). 주인만, 비회원 401 → 남 403 → 400·409. */
    @PutMapping("/api/categories/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                        @RequestBody List<CategoryOrderItem> items) {
        blogOwnerGuard.requireOwner(blog, member);
        categoryService.reorder(blog, items.stream()
                .map(item -> new CategoryService.OrderItem(item.id(), item.parentId(), item.sortOrder()))
                .toList());
    }

    @DeleteMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id) {
        categoryService.find(blog, id);
        blogOwnerGuard.requireOwner(blog, member);
        categoryService.delete(blog, id);
    }

}
