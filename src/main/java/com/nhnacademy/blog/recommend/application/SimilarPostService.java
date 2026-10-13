package com.nhnacademy.blog.recommend.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.recommend.domain.NearPost;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비슷한 글 (T069c, OWN-06). 이 글과 임베딩이 가까운 글 가운데 보는 사람이 볼 수 있는 글을 가까운 순으로.
 * <p>
 * 임베딩 표에는 비공개·숨긴 글도 있으므로(PostEmbeddingService) 가시성(visibleTo)을 같은 쿼리에서 건다
 * (PostEmbeddingRepository.findNear, T126). 볼 수 없는 글을 빼고도 size개를 채운다.
 * <p>
 * 추천을 꺼 두면(app.recommend.enabled=false) 임베딩을 만들지 않으므로 늘 빈 목록이다.
 */
@Service
public class SimilarPostService {

    private final PostReadService postReadService;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final Clock clock;

    public SimilarPostService(PostReadService postReadService, PostEmbeddingRepository postEmbeddingRepository,
                              Clock clock) {
        this.postReadService = postReadService;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.clock = clock;
    }

    /**
     * 이 글과 비슷한 글 size개, 가까운 순. 이 글을 볼 수 없으면 글 상세와 같이 404·403.
     * 이 글의 임베딩이 아직 없으면 빈 목록(contracts "추천이 준비 안 됐으면 빈 배열").
     * 다른 블로그의 글도 나온다. 볼 수 있는지는 보는 사람 기준(visibleTo)이다.
     */
    @Transactional(readOnly = true)
    public List<Post> similar(Blog blog, Long postId, Long viewerId, int size) {
        postReadService.readable(blog, postId, viewerId);
        return postEmbeddingRepository.findEmbedding(postId)
                .map(embedding -> postEmbeddingRepository.findNear(embedding,
                        PostSpecifications.visibleTo(viewerId, LocalDateTime.now(clock)), null, postId,
                        0, size, "blog", "category"))
                .orElse(List.of())
                .stream()
                .map(NearPost::post)
                .toList();
    }

}
