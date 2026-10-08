package com.nhnacademy.blog.home.application;

import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 최신 글 (T042, HOME-01). 모든 블로그에서 보는 사람이 볼 수 있는 글을 최신순으로 20개씩 더보기.
 * 페이지 번호가 아니라 (처음 발행 시각, id) 커서로 이어 읽어, 더보기 중에 새 글이 올라와도
 * 같은 글이 두 번 나오거나 빠지지 않는다(spec US3 시나리오 2).
 */
@Service
public class HomeService {

    public static final int LATEST_SIZE = 20;

    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final Clock clock;

    public HomeService(PostRepository postRepository, Clock clock) {
        this.postRepository = postRepository;
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

    /** 최신순에서 커서 뒤: 더 먼저 발행됐거나, 같은 시각이면 id가 더 작은 글. */
    private static Specification<Post> after(TimeIdCursor cursor) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("publishedAt"), cursor.time()),
                cb.and(cb.equal(root.get("publishedAt"), cursor.time()), cb.lessThan(root.get("id"), cursor.id())));
    }

}
