package com.nhnacademy.blog.ranking.presentation;

import com.nhnacademy.blog.blog.application.PrimaryBlogAddresses;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.home.application.PopularPosts;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.image.application.ProfileImages;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;
import com.nhnacademy.blog.ranking.application.RankingService;
import com.nhnacademy.blog.ranking.presentation.dto.BlogCardResponse;
import com.nhnacademy.blog.ranking.presentation.dto.RankedBlogsResponse;
import com.nhnacademy.blog.ranking.presentation.dto.RankedPostsResponse;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기 블로거(홈 5), 랭킹 전체보기(인기 글·인기 블로거 100위, 20개씩), 추천 블로그 5 (T080, HOME-04·05, SUB-06).
 * 모두 플랫폼 주소에서 누구나 부른다.
 */
@RestController
public class RankingController {

    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final RankingService rankingService;
    private final SubscriptionRepository subscriptionRepository;
    private final PrimaryBlogAddresses primaryBlogAddresses;
    private final ProfileImages profileImages;
    private final PostThumbnails postThumbnails;
    private final PostRepository postRepository;
    private final Clock clock;

    public RankingController(RankingService rankingService, SubscriptionRepository subscriptionRepository,
                             PrimaryBlogAddresses primaryBlogAddresses, ProfileImages profileImages,
                             PostThumbnails postThumbnails, PostRepository postRepository, Clock clock) {
        this.rankingService = rankingService;
        this.subscriptionRepository = subscriptionRepository;
        this.primaryBlogAddresses = primaryBlogAddresses;
        this.profileImages = profileImages;
        this.postThumbnails = postThumbnails;
        this.postRepository = postRepository;
        this.clock = clock;
    }

    @GetMapping("/api/home/bloggers")
    @Transactional(readOnly = true)
    public RankedBlogsResponse homeBloggers() {
        RankingService.RankedBlogs ranked = rankingService.bloggers();
        List<Blog> top = ranked.blogs().stream().limit(RankingService.HOME_SIZE).toList();
        return new RankedBlogsResponse(DateTimes.toOffset(ranked.snapshotAt()), cards(top, 0, false), null);
    }

    @GetMapping("/api/ranking/bloggers")
    @Transactional(readOnly = true)
    public RankedBlogsResponse rankingBloggers(@RequestParam(required = false) String snapshotAt,
                                               @RequestParam(required = false) Integer offset) {
        int start = offset == null ? 0 : offset;
        RankingService.RankedBlogs page = rankingService.bloggerPage(snapshotAt, offset);
        return new RankedBlogsResponse(DateTimes.toOffset(page.snapshotAt()), cards(page.blogs(), start, false),
                next(start, page.blogs().size()));
    }

    @GetMapping("/api/ranking/posts")
    public RankedPostsResponse rankingPosts(@RequestParam(required = false) String snapshotAt,
                                            @RequestParam(required = false) Integer offset) {
        int start = offset == null ? 0 : offset;
        PopularPosts page = rankingService.postPage(snapshotAt, offset);
        Map<Long, String> thumbnails = postThumbnails.of(page.posts());
        List<RankedPostsResponse.Item> items = IntStream.range(0, page.posts().size())
                .mapToObj(i -> new RankedPostsResponse.Item(start + i + 1, PostSummaryResponse.of(page.posts().get(i),
                        page.posts().get(i).getBlog(), thumbnails.get(page.posts().get(i).getId()))))
                .toList();
        return new RankedPostsResponse(DateTimes.toOffset(page.snapshotAt()), items, next(start, items.size()));
    }

    @GetMapping("/api/recommend/blogs")
    @Transactional(readOnly = true)
    public List<BlogCardResponse> recommend() {
        return cards(rankingService.recommend(LoginMembers.currentId()).blogs(), 0, true);
    }

    /** 다음 묶음의 시작. 이번 묶음이 20개가 안 되거나 100위에 닿으면 끝(null). */
    private static Integer next(int start, int size) {
        int next = start + size;
        return size == RankingService.PAGE_SIZE && next < RankingService.MAX_RANK ? next : null;
    }

    private List<BlogCardResponse> cards(List<Blog> blogs, int start, boolean withRecentPost) {
        List<Long> blogIds = blogs.stream().map(Blog::getId).toList();
        Map<Long, Long> subscribers = new HashMap<>();
        if (!blogIds.isEmpty()) {
            subscriptionRepository.countByBlogIds(blogIds)
                    .forEach(row -> subscribers.put((Long) row[0], (Long) row[1]));
        }
        List<Member> owners = blogs.stream().map(Blog::getMember).toList();
        Map<Long, String> addresses = primaryBlogAddresses.of(owners.stream().map(Member::getId).toList(), null);
        List<Long> imageIds = new ArrayList<>();
        owners.forEach(owner -> imageIds.add(owner.getProfileImageId()));
        blogs.forEach(blog -> imageIds.add(blog.getProfileImageId()));
        Map<Long, String> photos = profileImages.thumbnailUrls(imageIds);
        return IntStream.range(0, blogs.size()).mapToObj(i -> {
            Blog blog = blogs.get(i);
            Member owner = blog.getMember();
            return new BlogCardResponse(start + i + 1,
                    new BlogCardResponse.BlogRef(blog.getId(), blog.getAddress(), blog.getName(), blog.getDescription(),
                            photos.get(blog.getProfileImageId())),
                    MemberSummaryResponse.of(owner, addresses.get(owner.getId()), photos.get(owner.getProfileImageId())),
                    subscribers.getOrDefault(blog.getId(), 0L),
                    withRecentPost ? recentPostTitle(blog) : null);
        }).toList();
    }

    private String recentPostTitle(Blog blog) {
        List<Post> posts = postRepository.findBy(PostSpecifications.listedIn(blog, null, LocalDateTime.now(clock)),
                query -> query.sortBy(LATEST).limit(1).all());
        return posts.isEmpty() ? null : posts.getFirst().getTitle();
    }

}
