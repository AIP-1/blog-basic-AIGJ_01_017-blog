package com.nhnacademy.blog.category.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.application.CategoryService;
import com.nhnacademy.blog.category.application.CategoryTreeService;
import com.nhnacademy.blog.category.presentation.dto.CategoryCreatedResponse;
import com.nhnacademy.blog.category.presentation.dto.CategoryRequest;
import com.nhnacademy.blog.category.presentation.dto.CategoryTreeResponse;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.RequestValidator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PatchMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id,
                       @RequestBody CategoryRequest request) {
        categoryService.find(blog, id);
        blogOwnerGuard.requireOwner(blog, member);
        requestValidator.validate(request);
        if (request.parentId() != null) {
            throw BusinessException.invalidField("parentId", "상위 카테고리는 이름 변경에서 바꿀 수 없습니다.");
        }
        categoryService.rename(blog, id, request.name());
    }

    @DeleteMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member, @PathVariable Long id) {
        categoryService.find(blog, id);
        blogOwnerGuard.requireOwner(blog, member);
        categoryService.delete(blog, id);
    }

}
