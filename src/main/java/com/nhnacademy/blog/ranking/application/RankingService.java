package com.nhnacademy.blog.ranking.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.home.application.HomeService;
import com.nhnacademy.blog.home.application.PopularPosts;
import com.nhnacademy.blog.ranking.domain.BlogScore;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인기 블로거·랭킹 전체보기·추천 블로그 (T080, HOME-04·05, SUB-06). 모두 같은 스냅숏을 읽는다(R-13).
 * 순위의 블로그는 읽을 때 지금도 남에게 보이는지 다시 본다(그 사이 이용 제한·정지·삭제되면 빠지고 다음 순위가 올라온다).
 */
@Service
public class RankingService {

    public static final int HOME_SIZE = 5;
    public static final int PAGE_SIZE = 20;
    public static final int MAX_RANK = 100;

    private final BloggerRanking bloggerRanking;
    private final HomeService homeService;
    private final BlogRepository blogRepository;
    private final BlogVisibilityPolicy blogVisibilityPolicy;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public RankingService(BloggerRanking bloggerRanking, HomeService homeService, BlogRepository blogRepository,
                          BlogVisibilityPolicy blogVisibilityPolicy, SubscriptionRepository subscriptionRepository,
                          Clock clock) {
        this.bloggerRanking = bloggerRanking;
        this.homeService = homeService;
        this.blogRepository = blogRepository;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    /** 지금 시간 칸의 인기 블로거, 볼 수 있는 블로그만 순위대로(최대 100). */
    @Transactional(readOnly = true)
    public RankedBlogs bloggers() {
        BloggerSnapshot snapshot = bloggerRanking.snapshot(LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS));
        List<Long> ids = snapshot.scores().stream().map(BlogScore::blogId).toList();
        Map<Long, Blog> blogs = blogRepository.findAllWithMemberByIdIn(ids).stream()
                .collect(Collectors.toMap(Blog::getId, Function.identity()));
        List<Blog> visible = ids.stream()
                .map(blogs::get)
                .filter(Objects::nonNull)
                .filter(blog -> blogVisibilityPolicy.isOpenToOthers(blog) && !blog.isMoved())
                .toList();
        return new RankedBlogs(snapshot.snapshotAt(), visible);
    }

    /** 랭킹 전체보기의 인기 블로거 한 묶음(20). snapshotAt이 지금 순위와 다르면 409 RANKING_UPDATED. */
    @Transactional(readOnly = true)
    public RankedBlogs bloggerPage(String snapshotAt, Integer offset) {
        int start = offset(offset);
        RankedBlogs all = bloggers();
        requireSameSnapshot(snapshotAt, all.snapshotAt());
        return new RankedBlogs(all.snapshotAt(), slice(all.blogs(), start));
    }

    /** 랭킹 전체보기의 인기 글 한 묶음(20). 홈 인기 글과 같은 스냅숏(5분)이다. */
    @Transactional(readOnly = true)
    public PopularPosts postPage(String snapshotAt, Integer offset) {
        int start = offset(offset);
        PopularPosts all = homeService.popularRanking(MAX_RANK);
        requireSameSnapshot(snapshotAt, all.snapshotAt());
        return new PopularPosts(all.snapshotAt(), slice(all.posts(), start));
    }

    /**
     * 추천 블로그 5 (SUB-06): 인기 블로거 순위에서 내 블로그와 이미 구독한 블로그를 뺀 위에서부터.
     * 비회원은 인기 블로거와 같다.
     */
    @Transactional(readOnly = true)
    public RankedBlogs recommend(Long viewerId) {
        RankedBlogs all = bloggers();
        Set<Long> subscribed = viewerId == null ? Set.of() : Set.copyOf(subscriptionRepository.findBlogIdsByMemberId(viewerId));
        List<Blog> picked = all.blogs().stream()
                .filter(blog -> !blog.isOwnedBy(viewerId))
                .filter(blog -> !subscribed.contains(blog.getId()))
                .limit(HOME_SIZE)
                .toList();
        return new RankedBlogs(all.snapshotAt(), picked);
    }

    private static int offset(Integer offset) {
        int value = offset == null ? 0 : offset;
        if (value < 0 || value >= MAX_RANK) {
            throw BusinessException.invalidField("offset", "0부터 " + (MAX_RANK - 1) + "까지입니다.");
        }
        return value;
    }

    private static void requireSameSnapshot(String requested, LocalDateTime current) {
        if (requested == null || requested.isBlank()) {
            return;
        }
        OffsetDateTime parsed;
        try {
            parsed = OffsetDateTime.parse(requested);
        } catch (DateTimeParseException e) {
            throw BusinessException.invalidField("snapshotAt", "기준 시각 형식이 올바르지 않습니다.");
        }
        OffsetDateTime now = DateTimes.toOffset(current);
        if (!parsed.toInstant().equals(now.toInstant())) {
            throw new BusinessException(ErrorCode.RANKING_UPDATED, Map.of("snapshotAt", now));
        }
    }

    private static <T> List<T> slice(List<T> list, int start) {
        if (start >= list.size()) {
            return List.of();
        }
        return list.subList(start, Math.min(start + PAGE_SIZE, list.size()));
    }

    public record RankedBlogs(LocalDateTime snapshotAt, List<Blog> blogs) {
    }

}
