package com.nhnacademy.blog.subscription.presentation;

import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.web.CursorResponse;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import com.nhnacademy.blog.subscription.application.FeedService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구독 피드 (T090, SUB-02). 회원만. 응답 모양은 홈 최신 글과 같은 PostSummary 커서 목록이다.
 */
@RestController
public class FeedController {

    private final FeedService feedService;
    private final PostThumbnails postThumbnails;

    public FeedController(FeedService feedService, PostThumbnails postThumbnails) {
        this.feedService = feedService;
        this.postThumbnails = postThumbnails;
    }

    @GetMapping("/api/feed")
    @PreAuthorize("isAuthenticated()")
    public CursorResponse<PostSummaryResponse> feed(@AuthenticationPrincipal LoginMember member,
                                                    @RequestParam(required = false) String cursor) {
        List<Post> posts = feedService.feed(member.id(), TimeIdCursor.decode(cursor));
        CursorResponse<Post> page = CursorResponse.of(posts, FeedService.PAGE_SIZE,
                last -> new TimeIdCursor(last.getPublishedAt(), last.getId()).encode());
        Map<Long, String> thumbnails = postThumbnails.of(page.content());
        return new CursorResponse<>(page.content().stream()
                .map(post -> PostSummaryResponse.of(post, post.getBlog(), thumbnails.get(post.getId())))
                .toList(), page.nextCursor());
    }

}
