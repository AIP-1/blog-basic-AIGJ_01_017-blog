package com.nhnacademy.blog.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * 의미 검색을 할 수 없을 때 (T121). 글자 검색으로 몰래 바꾸지 않고 503 SEMANTIC_SEARCH_UNAVAILABLE.
 */
class SemanticSearchServiceTest {

    PostEmbeddingRepository postEmbeddingRepository = mock(PostEmbeddingRepository.class);
    SemanticSearchProperties properties = new SemanticSearchProperties(0.4, 100);
    PageQuery page = PageQuery.of(null, null, 10);

    @Test
    void embeddingServerDownIsUnavailable() {
        QueryEmbeddings down = mock(QueryEmbeddings.class);
        when(down.embed(anyString())).thenThrow(new ResourceAccessException("Connection refused"));
        SemanticSearchService service = new SemanticSearchService(Optional.of(down), postEmbeddingRepository,
                properties, Clock.systemUTC());

        assertThatThrownBy(() -> service.searchAll(null, "자바", page))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.SEMANTIC_SEARCH_UNAVAILABLE));
    }

    @Test
    void emptyQueryIsCheckedBeforeAvailability() {
        SemanticSearchService service = new SemanticSearchService(Optional.empty(), postEmbeddingRepository,
                properties, Clock.systemUTC());

        assertThatThrownBy(() -> service.searchAll(null, " ", page))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

}
