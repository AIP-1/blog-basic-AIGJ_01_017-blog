package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.recommend.domain.NearPost;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 의미 검색 (T121, OWN-07, R-02). 검색어와 뜻이 가까운 글을 가까운 순으로.
 * <ol>
 *   <li>검색어를 글과 같은 모델로 임베딩한다(QueryEmbeddings, 캐시 5분)</li>
 *   <li>추천 DB(PostgreSQL)에서 코사인 유사도가 min-similarity 이상인 글을 가까운 순 max-results개까지 뽑는다</li>
 *   <li>주 DB(MySQL)에서 보는 사람이 볼 수 있는 글만 남기고, 2의 순서대로 페이지를 나눈다</li>
 * </ol>
 * 임베딩 표에는 비공개·숨긴 글도 있으므로 가시성은 반드시 3에서 거른다. 규칙은 글자 검색(SearchService)과 같다:
 * 전체 검색은 남에게 보이는 글(visibleTo), 블로그 안 검색은 블로그 화면과 같은 글(listedIn, 주인에게는 자기 비공개 글도).
 * 블로그 안 검색은 추천 DB가 블로그를 모르므로, MySQL에서 그 블로그의 볼 수 있는 글 번호를 먼저 읽어 그 안에서만 찾는다.
 * <p>
 * 추천을 꺼 뒀거나(부품 없음) 임베딩 서버·추천 DB에 닿지 않으면 503 SEMANTIC_SEARCH_UNAVAILABLE.
 * 글자 검색으로 대신 돌려주지 않는다(화면이 안내하고 사용자가 탭을 고른다).
 */
@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final PostRepository postRepository;
    private final Optional<QueryEmbeddings> queryEmbeddings;
    private final Optional<PostEmbeddingRepository> postEmbeddingRepository;
    private final SemanticSearchProperties properties;
    private final Clock clock;

    public SemanticSearchService(PostRepository postRepository, Optional<QueryEmbeddings> queryEmbeddings,
                                 Optional<PostEmbeddingRepository> postEmbeddingRepository,
                                 SemanticSearchProperties properties, Clock clock) {
        this.postRepository = postRepository;
        this.queryEmbeddings = queryEmbeddings;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** 블로그 안 의미 검색. 결과의 글은 모두 이 블로그 것이라 블로그를 함께 읽지 않는다. */
    @Transactional(readOnly = true)
    public Page<SemanticHit> search(Blog blog, Long viewerId, String rawQuery, PageQuery page) {
        String query = SearchService.normalize(rawQuery);
        Specification<Post> listed = PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock));
        List<NearPost> nearest = nearest(query, postRepository.findIds(listed));
        return page(nearest, listed, page, "category");
    }

    /** 전체 의미 검색. 목록 한 줄에 블로그 이름과 카테고리가 나가므로 함께 읽는다. */
    @Transactional(readOnly = true)
    public Page<SemanticHit> searchAll(Long viewerId, String rawQuery, PageQuery page) {
        String query = SearchService.normalize(rawQuery);
        List<NearPost> nearest = nearest(query, null);
        return page(nearest, PostSpecifications.visibleTo(viewerId, LocalDateTime.now(clock)), page, "blog", "category");
    }

    /** within이 null이면 모든 글에서, 아니면 그 번호 안에서 가까운 글. */
    private List<NearPost> nearest(String query, Collection<Long> within) {
        if (queryEmbeddings.isEmpty() || postEmbeddingRepository.isEmpty()) {
            throw new BusinessException(ErrorCode.SEMANTIC_SEARCH_UNAVAILABLE);
        }
        try {
            float[] vector = queryEmbeddings.get().embed(query);
            return postEmbeddingRepository.get()
                    .findNearestTo(vector, properties.minSimilarity(), properties.maxResults(), within);
        } catch (RuntimeException e) {
            log.warn("의미 검색을 하지 못했습니다(임베딩 서버·추천 DB): {}", e.toString());
            throw new BusinessException(ErrorCode.SEMANTIC_SEARCH_UNAVAILABLE);
        }
    }

    /** 가까운 순서를 지키며 볼 수 있는 글만 남기고, 그중 이 페이지 몫을 자른다. 전체 수는 볼 수 있는 글 수다. */
    private Page<SemanticHit> page(List<NearPost> nearest, Specification<Post> visible, PageQuery page,
                                   String... fetch) {
        Pageable pageable = page.toPageable(Sort.unsorted());
        if (nearest.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<Long> ids = nearest.stream().map(NearPost::postId).toList();
        Specification<Post> condition = visible.and((root, criteria, cb) -> root.get("id").in(ids));
        Map<Long, Post> posts = postRepository.findBy(condition, q -> q.project(fetch).all()).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<SemanticHit> hits = nearest.stream()
                .filter(near -> posts.containsKey(near.postId()))
                .map(near -> new SemanticHit(posts.get(near.postId()), near.similarity()))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), hits.size());
        int to = Math.min(from + pageable.getPageSize(), hits.size());
        return new PageImpl<>(hits.subList(from, to), pageable, hits.size());
    }

}
