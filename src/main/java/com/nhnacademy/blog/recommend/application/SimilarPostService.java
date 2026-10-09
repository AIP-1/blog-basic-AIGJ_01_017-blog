package com.nhnacademy.blog.recommend.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비슷한 글 (T069c, OWN-06). 임베딩이 가까운 글을 PostgreSQL에서 후보로 뽑고, MySQL에서 보는 사람이 볼 수 있는 글만 남긴다.
 * <p>
 * 임베딩 표에는 비공개·숨긴 글도 있으므로(PostEmbeddingService) <b>가시성은 반드시 여기서</b> 거른다.
 * 후보를 넉넉히(30개) 뽑는 이유: 볼 수 없는 글을 빼고도 size개를 채우려고.
 */
@Service
public class SimilarPostService {

    static final int CANDIDATES = 30;

    private static final Logger log = LoggerFactory.getLogger(SimilarPostService.class);

    private final PostReadService postReadService;
    private final PostRepository postRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final Clock clock;

    public SimilarPostService(PostReadService postReadService, PostRepository postRepository,
                              PostEmbeddingRepository postEmbeddingRepository, Clock clock) {
        this.postReadService = postReadService;
        this.postRepository = postRepository;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.clock = clock;
    }

    /**
     * 이 글과 비슷한 글 size개, 가까운 순. 이 글을 볼 수 없으면 글 상세와 같이 404·403.
     * 이 글의 임베딩이 아직 없거나 추천 DB에 닿지 않으면 빈 목록(contracts "추천이 준비 안 됐으면 빈 배열").
     * 다른 블로그의 글도 나온다. 볼 수 있는지는 보는 사람 기준(visibleTo)이다.
     */
    @Transactional(readOnly = true)
    public List<Post> similar(Blog blog, Long postId, Long viewerId, int size) {
        postReadService.readable(blog, postId, viewerId);
        List<Long> nearest = nearest(postId);
        if (nearest.isEmpty()) {
            return List.of();
        }
        Specification<Post> condition = PostSpecifications.visibleTo(viewerId, LocalDateTime.now(clock))
                .and((root, query, cb) -> root.get("id").in(nearest));
        Map<Long, Post> visible = postRepository.findBy(condition, query -> query.project("blog", "category").all())
                .stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return nearest.stream()
                .map(visible::get)
                .filter(Objects::nonNull)
                .limit(size)
                .toList();
    }

    private List<Long> nearest(Long postId) {
        try {
            return postEmbeddingRepository.findNearest(postId, CANDIDATES);
        } catch (DataAccessException e) {
            log.warn("비슷한 글 후보를 읽지 못했습니다(추천 DB): {}", e.toString());
            return List.of();
        }
    }

}
