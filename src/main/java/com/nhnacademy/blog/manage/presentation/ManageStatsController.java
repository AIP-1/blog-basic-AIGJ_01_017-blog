package com.nhnacademy.blog.manage.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.manage.application.BlogStatsService;
import com.nhnacademy.blog.manage.application.ManageCommentService;
import com.nhnacademy.blog.manage.presentation.dto.ReceivedCommentResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 방문 통계 (T083, MNG-03). 블로그 주소에서 주인만. */
@RestController
public class ManageStatsController {

    private final BlogStatsService blogStatsService;
    private final ManageCommentService manageCommentService;
    private final BlogOwnerGuard blogOwnerGuard;

    public ManageStatsController(BlogStatsService blogStatsService, ManageCommentService manageCommentService,
                                 BlogOwnerGuard blogOwnerGuard) {
        this.blogStatsService = blogStatsService;
        this.manageCommentService = manageCommentService;
        this.blogOwnerGuard = blogOwnerGuard;
    }

    /** 관리 홈 `{ today, yesterday, total, recentComments[5], recentPosts[5] }`. */
    @GetMapping("/api/manage/stats")
    public Summary summary(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member) {
        blogOwnerGuard.requireOwner(blog, member);
        BlogStatsService.Summary summary = blogStatsService.summary(blog);
        List<ReceivedCommentResponse> comments = manageCommentService.comments(blog, new PageQuery(1, 5)).getContent()
                .stream().map(ReceivedCommentResponse::from).toList();
        List<RecentPost> posts = summary.recentPosts().stream()
                .map(post -> new RecentPost(post.getId(), post.getTitle(), post.getViewCount(),
                        DateTimes.toOffset(post.getPublishedAt())))
                .toList();
        return new Summary(summary.today(), summary.yesterday(), summary.total(), comments, posts);
    }

    @GetMapping("/api/manage/stats/visitors")
    public List<Point> visitors(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                @RequestParam(required = false) String unit,
                                @RequestParam(required = false) String from,
                                @RequestParam(required = false) String to) {
        blogOwnerGuard.requireOwner(blog, member);
        return blogStatsService.visitors(blog, unit, from, to).stream()
                .map(point -> new Point(point.date(), point.visitorCount(), point.viewCount())).toList();
    }

    @GetMapping("/api/manage/stats/popular-posts")
    public List<PopularPost> popularPosts(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                          @RequestParam(required = false) String range) {
        blogOwnerGuard.requireOwner(blog, member);
        List<BlogStatsService.PopularPost> posts = blogStatsService.popularPosts(blog, range);
        return IntStream.range(0, posts.size())
                .mapToObj(i -> new PopularPost(i + 1, new PostRef(posts.get(i).postId(), posts.get(i).title()),
                        posts.get(i).viewCount()))
                .toList();
    }

    @GetMapping("/api/manage/stats/referrers")
    public BlogStatsService.Referrers referrers(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        blogOwnerGuard.requireOwner(blog, member);
        return blogStatsService.referrers(blog, from, to);
    }

    public record Summary(long today, long yesterday, long total, List<ReceivedCommentResponse> recentComments,
                          List<RecentPost> recentPosts) {
    }

    public record RecentPost(Long id, String title, long viewCount, OffsetDateTime publishedAt) {
    }

    public record Point(LocalDate date, long visitorCount, long viewCount) {
    }

    public record PopularPost(int rank, PostRef post, long viewCount) {
    }

    public record PostRef(Long id, String title) {
    }

}
