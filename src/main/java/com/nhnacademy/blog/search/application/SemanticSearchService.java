package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
 *   <li>글과 임베딩을 한 쿼리로 묶어, 보는 사람이 볼 수 있는 글 가운데 코사인 유사도가 min-similarity 이상인 글을
 *       가까운 순으로 읽는다(PostEmbeddingRepository.findNear, T126)</li>
 *   <li>가까운 순 max-results개 안에서만 페이지를 나눈다(의미 검색은 앞쪽 결과만 쓸모가 있다)</li>
 * </ol>
 * 임베딩 표에는 비공개·숨긴 글도 있으므로 가시성은 반드시 같은 쿼리에서 건다. 규칙은 글자 검색(SearchService)과 같다:
 * 전체 검색은 남에게 보이는 글(visibleTo), 블로그 안 검색은 블로그 화면과 같은 글(listedIn, 주인에게는 자기 비공개 글도).
 * <p>
 * 추천을 꺼 뒀거나(임베딩 부품 없음) 임베딩 서버에 닿지 않으면 503 SEMANTIC_SEARCH_UNAVAILABLE.
 * 임베딩은 주 DB에 있으므로 DB 오류는 다른 API와 같이 500이다.
 * 글자 검색으로 대신 돌려주지 않는다(화면이 안내하고 사용자가 탭을 고른다).
 */
@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final Optional<QueryEmbeddings> queryEmbeddings;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final SemanticSearchProperties properties;
    private final Clock clock;

    public SemanticSearchService(Optional<QueryEmbeddings> queryEmbeddings,
                                 PostEmbeddingRepository postEmbeddingRepository,
                                 SemanticSearchProperties properties, Clock clock) {
        this.queryEmbeddings = queryEmbeddings;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** 블로그 안 의미 검색. 결과의 글은 모두 이 블로그 것이라 블로그를 함께 읽지 않는다. */
    @Transactional(readOnly = true)
    public Page<SemanticHit> search(Blog blog, Long viewerId, String rawQuery, PageQuery page) {
        String query = SearchService.normalize(rawQuery);
        return search(query, PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock)), page, "category");
    }

    /** 전체 의미 검색. 목록 한 줄에 블로그 이름과 카테고리가 나가므로 함께 읽는다. */
    @Transactional(readOnly = true)
    public Page<SemanticHit> searchAll(Long viewerId, String rawQuery, PageQuery page) {
        String query = SearchService.normalize(rawQuery);
        return search(query, PostSpecifications.visibleTo(viewerId, LocalDateTime.now(clock)), page,
                "blog", "category");
    }

    /** 가까운 순 max-results개 안에서 이 페이지 몫. 전체 수도 max-results를 넘지 않는다. */
    private Page<SemanticHit> search(String query, Specification<Post> visible, PageQuery page, String... fetch) {
        float[] vector = embed(query);
        Pageable pageable = page.toPageable(Sort.unsorted());
        long total = Math.min(postEmbeddingRepository.countNear(vector, visible, properties.minSimilarity(), null),
                properties.maxResults());
        int offset = (int) Math.min(pageable.getOffset(), total);
        int limit = (int) Math.min(pageable.getPageSize(), total - offset);
        if (limit == 0) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        List<SemanticHit> hits = postEmbeddingRepository
                .findNear(vector, visible, properties.minSimilarity(), null, offset, limit, fetch).stream()
                .map(near -> new SemanticHit(near.post(), near.similarity()))
                .toList();
        return new PageImpl<>(hits, pageable, total);
    }

    private float[] embed(String query) {
        if (queryEmbeddings.isEmpty()) {
            throw new BusinessException(ErrorCode.SEMANTIC_SEARCH_UNAVAILABLE);
        }
        try {
            return queryEmbeddings.get().embed(query);
        } catch (RuntimeException e) {
            log.warn("검색어를 임베딩하지 못했습니다(임베딩 서버): {}", e.toString());
            throw new BusinessException(ErrorCode.SEMANTIC_SEARCH_UNAVAILABLE);
        }
    }

}
