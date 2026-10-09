package com.nhnacademy.blog.home.presentation;

import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.web.CursorResponse;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.home.application.HomeService;
import com.nhnacademy.blog.home.application.PopularPosts;
import com.nhnacademy.blog.home.presentation.dto.PopularPostsResponse;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 최신 글 (T042, HOME-01)과 인기 글 (T063, HOME-02). 플랫폼 주소에서 부른다. 주제별 글은 스텝 9에서 더한다.
 */
@RestController
public class HomeController {

    private final HomeService homeService;
    private final PostThumbnails postThumbnails;

    public HomeController(HomeService homeService, PostThumbnails postThumbnails) {
        this.homeService = homeService;
        this.postThumbnails = postThumbnails;
    }

    /** 처음엔 cursor 없이, 다음부터는 받은 nextCursor를 그대로. 끝이면 nextCursor가 null이다. */
    @GetMapping("/api/home/latest")
    public CursorResponse<PostSummaryResponse> latest(@RequestParam(required = false) String cursor) {
        List<Post> posts = homeService.latest(LoginMembers.currentId(), TimeIdCursor.decode(cursor));
        CursorResponse<Post> page = CursorResponse.of(posts, HomeService.LATEST_SIZE,
                last -> new TimeIdCursor(last.getPublishedAt(), last.getId()).encode());
        Map<Long, String> thumbnails = postThumbnails.of(page.content());
        return new CursorResponse<>(page.content().stream()
                .map(post -> PostSummaryResponse.of(post, post.getBlog(), thumbnails.get(post.getId())))
                .toList(), page.nextCursor());
    }

    /** 인기 점수 순 공개 글 10개와 순위를 계산한 시각. 순위는 5분마다 바뀐다. */
    @GetMapping("/api/home/popular")
    public PopularPostsResponse popular() {
        PopularPosts popular = homeService.popular();
        List<Post> posts = popular.posts();
        Map<Long, String> thumbnails = postThumbnails.of(posts);
        return new PopularPostsResponse(DateTimes.toOffset(popular.snapshotAt()), IntStream.range(0, posts.size())
                .mapToObj(i -> new PopularPostsResponse.Item(i + 1, PostSummaryResponse.of(posts.get(i),
                        posts.get(i).getBlog(), thumbnails.get(posts.get(i).getId()))))
                .toList());
    }

}
