package com.nhnacademy.blog.manage.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.global.web.RequestValidator;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.manage.application.ManagePostFilter;
import com.nhnacademy.blog.manage.application.ManagePostService;
import com.nhnacademy.blog.manage.application.ManagedPostPage;
import com.nhnacademy.blog.manage.presentation.dto.BulkDeleteRequest;
import com.nhnacademy.blog.manage.presentation.dto.BulkResult;
import com.nhnacademy.blog.manage.presentation.dto.BulkVisibilityRequest;
import com.nhnacademy.blog.manage.presentation.dto.ManagedPostSummaryResponse;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Visibility;
import java.util.Arrays;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 글 관리 (T067, MNG-01). 블로그 주소에서 부르고, 블로그 주인만 쓴다.
 * 상태 코드 순서: 비회원 401 → 주인 아님 403 → 입력 400. 그래서 거르기 값(status 등)도 주인 확인 뒤에 직접 읽는다.
 */
@RestController
public class ManagePostController {

    private static final int MAX_QUERY_LENGTH = 100;

    private final ManagePostService managePostService;
    private final BlogOwnerGuard blogOwnerGuard;
    private final RequestValidator requestValidator;
    private final PostThumbnails postThumbnails;

    public ManagePostController(ManagePostService managePostService, BlogOwnerGuard blogOwnerGuard,
                                RequestValidator requestValidator, PostThumbnails postThumbnails) {
        this.managePostService = managePostService;
        this.blogOwnerGuard = blogOwnerGuard;
        this.requestValidator = requestValidator;
        this.postThumbnails = postThumbnails;
    }

    /** 내 글 20개씩. status·visibility·categoryId(0은 미분류)·q(제목)로 거른다. */
    @GetMapping("/api/manage/posts")
    public PageResponse<ManagedPostSummaryResponse> posts(@CurrentBlog Blog blog,
                                                          @AuthenticationPrincipal LoginMember member,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String visibility,
                                                          @RequestParam(required = false) Long categoryId,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Integer page) {
        blogOwnerGuard.requireOwner(blog, member);
        ManagePostFilter filter = new ManagePostFilter(parse(PostStatus.class, "status", status),
                parse(Visibility.class, "visibility", visibility), categoryId, query(q));
        ManagedPostPage result = managePostService.posts(blog, filter,
                PageQuery.of(page, null, ManagePostService.PAGE_SIZE));
        Map<Long, String> thumbnails = postThumbnails.of(result.posts().getContent());
        return PageResponse.from(result.posts(), post -> ManagedPostSummaryResponse.of(post,
                thumbnails.get(post.getId()), result.blindReasons().get(post.getId())));
    }

    /** 고른 글들의 공개 범위를 한 번에 바꾼다. 구독자 공개는 구독 기능(SUB-01) 전이라 400. */
    @PatchMapping("/api/manage/posts")
    public BulkResult.Updated changeVisibility(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                               @RequestBody BulkVisibilityRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        requestValidator.validate(request);
        if (request.visibility() == Visibility.SUBSCRIBERS) {
            throw BusinessException.invalidField("visibility", "구독자 공개는 아직 고를 수 없습니다.");
        }
        return new BulkResult.Updated(managePostService.changeVisibility(blog, request.postIds(),
                request.visibility()));
    }

    /** 고른 글들을 한 번에 지운다(소프트 삭제, 댓글·공감·알림도 함께). */
    @DeleteMapping("/api/manage/posts")
    public BulkResult.Deleted delete(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                     @RequestBody BulkDeleteRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        requestValidator.validate(request);
        return new BulkResult.Deleted(managePostService.delete(blog, request.postIds(), member));
    }

    /** 비었으면 거르지 않는다. 모르는 값이면 400(그 칸). */
    private static <E extends Enum<E>> E parse(Class<E> type, String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(value))
                .findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "알 수 없는 값입니다."));
    }

    private static String query(String q) {
        String query = q == null ? "" : q.strip();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw BusinessException.invalidField("q", "검색어는 " + MAX_QUERY_LENGTH + "자까지입니다.");
        }
        return query.isEmpty() ? null : query;
    }

}
