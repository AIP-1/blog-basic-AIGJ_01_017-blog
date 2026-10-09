package com.nhnacademy.blog.home.application;

import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.home.domain.PostScore;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.Topic;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 최신 글 (T042, HOME-01), 인기 글 (T063, HOME-02), 주제별 글 (T064, HOME-03).
 * 최신 글은 모든 블로그에서 보는 사람이 볼 수 있는 글을 최신순으로 20개씩 더보기.
 * 페이지 번호가 아니라 (처음 발행 시각, id) 커서로 이어 읽어, 더보기 중에 새 글이 올라와도
 * 같은 글이 두 번 나오거나 빠지지 않는다(spec US3 시나리오 2).
 */
@Service
public class HomeService {

    public static final int LATEST_SIZE = 20;
    public static final int POPULAR_SIZE = 10;
    public static final int TOPIC_SIZE = 6;

    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final PopularRanking popularRanking;
    private final Clock clock;

    public HomeService(PostRepository postRepository, PopularRanking popularRanking, Clock clock) {
        this.postRepository = postRepository;
        this.popularRanking = popularRanking;
        this.clock = clock;
    }

    /**
     * 커서 다음 글을 size + 1개 읽는다. 하나 더 읽은 것이 있으면 다음 묶음이 있다는 뜻이다(CursorResponse).
     * 목록 한 줄에 블로그 이름과 카테고리가 나가므로 함께 읽는다(project → 가져올 연관 지정).
     */
    @Transactional(readOnly = true)
    public List<Post> latest(Long viewerId, TimeIdCursor cursor) {
        Specification<Post> condition = PostSpecifications.visibleTo(viewerId, LocalDateTime.now(clock));
        if (cursor != null) {
            condition = condition.and(after(cursor));
        }
        return postRepository.findBy(condition,
                query -> query.sortBy(LATEST).project("blog", "category").limit(LATEST_SIZE + 1).all());
    }

    /** 인기 점수 순 공개 글 10개. 순위(글 번호)는 5분 캐시에서 읽고, 글은 매번 DB에서 읽으며 가시성을 다시 확인한다. */
    @Transactional(readOnly = true)
    public PopularPosts popular() {
        PopularSnapshot snapshot = popularRanking.snapshot();
        return new PopularPosts(snapshot.snapshotAt(), visibleInRankOrder(snapshot, POPULAR_SIZE));
    }

    /**
     * 주제별 글 6개 (HOME-03). 인기 글과 같은 점수 순(5분 캐시)이고, 6개가 안 되면 그 주제의 최신 글로 채운다.
     * 채우는 최신 글은 캐시하지 않는다(주제 인덱스 idx_post_topic_feed로 몇 개만 읽는다). 주제 없는 글은 어느 주제에도 없다.
     */
    @Transactional(readOnly = true)
    public List<Post> topicPosts(Topic topic) {
        List<Post> ranked = visibleInRankOrder(popularRanking.topicSnapshot(topic), TOPIC_SIZE);
        if (ranked.size() >= TOPIC_SIZE) {
            return ranked;
        }
        List<Long> rankedIds = ranked.stream().map(Post::getId).toList();
        Specification<Post> latest = PostSpecifications.visibleTo(null, LocalDateTime.now(clock))
                .and((root, query, cb) -> cb.equal(root.get("topic"), topic));
        if (!rankedIds.isEmpty()) {
            latest = latest.and((root, query, cb) -> cb.not(root.get("id").in(rankedIds)));
        }
        List<Post> filler = postRepository.findBy(latest, query -> query.sortBy(LATEST).project("blog", "category")
                .limit(TOPIC_SIZE - ranked.size()).all());
        return Stream.concat(ranked.stream(), filler.stream()).toList();
    }

    /**
     * 캐시된 순위의 글을 DB에서 읽어, 지금 볼 수 있는 글만 순위대로 limit개. 캐시된 5분 사이에 지워지거나 비공개·숨김이 된 글,
     * 블로그가 이용 제한된 글은 바로 빠지고 다음 순위가 올라온다. "공개 글" 순위라 모두에게 같은 목록이다(비회원 기준).
     */
    private List<Post> visibleInRankOrder(PopularSnapshot snapshot, int limit) {
        List<Long> rankedIds = snapshot.scores().stream().map(PostScore::postId).toList();
        if (rankedIds.isEmpty()) {
            return List.of();
        }
        Specification<Post> condition = PostSpecifications.visibleTo(null, LocalDateTime.now(clock))
                .and((root, query, cb) -> root.get("id").in(rankedIds));
        Map<Long, Post> visible = postRepository.findBy(condition, query -> query.project("blog", "category").all())
                .stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return rankedIds.stream()
                .map(visible::get)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();
    }

    /** 최신순에서 커서 뒤: 더 먼저 발행됐거나, 같은 시각이면 id가 더 작은 글. */
    private static Specification<Post> after(TimeIdCursor cursor) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("publishedAt"), cursor.time()),
                cb.and(cb.equal(root.get("publishedAt"), cursor.time()), cb.lessThan(root.get("id"), cursor.id())));
    }

}
