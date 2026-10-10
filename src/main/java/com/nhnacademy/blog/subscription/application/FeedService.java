package com.nhnacademy.blog.subscription.application;

import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.subscription.domain.Subscription;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독 피드 (T090, SUB-02). 구독한 블로그의 글 중 보는 사람이 볼 수 있는 것(홈과 같은 visibleTo라 구독자 공개 글도 나온다),
 * 최신순 20개씩 더보기. (발행 시각, id) 커서라 읽는 중에 새 글이 올라와도 겹치거나 빠지지 않는다(spec US6 시나리오 2).
 */
@Service
public class FeedService {

    public static final int PAGE_SIZE = 20;

    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final Clock clock;

    public FeedService(PostRepository postRepository, Clock clock) {
        this.postRepository = postRepository;
        this.clock = clock;
    }

    /** 다음 묶음이 있는지 보려고 하나 더 읽는다(CursorResponse). */
    @Transactional(readOnly = true)
    public List<Post> feed(Long memberId, TimeIdCursor cursor) {
        Specification<Post> subscribed = (root, query, cb) -> {
            Subquery<Long> mine = query.subquery(Long.class);
            Root<Subscription> subscription = mine.from(Subscription.class);
            mine.select(subscription.get("id")).where(
                    cb.equal(subscription.get("blog"), root.get("blog")),
                    cb.equal(subscription.get("member").get("id"), memberId));
            return cb.exists(mine);
        };
        Specification<Post> condition = PostSpecifications.visibleTo(memberId, LocalDateTime.now(clock))
                .and(subscribed);
        if (cursor != null) {
            condition = condition.and((root, query, cb) -> cb.or(
                    cb.lessThan(root.get("publishedAt"), cursor.time()),
                    cb.and(cb.equal(root.get("publishedAt"), cursor.time()), cb.lessThan(root.get("id"), cursor.id()))));
        }
        return postRepository.findBy(condition,
                query -> query.sortBy(LATEST).project("blog", "category").limit(PAGE_SIZE + 1).all());
    }

}
