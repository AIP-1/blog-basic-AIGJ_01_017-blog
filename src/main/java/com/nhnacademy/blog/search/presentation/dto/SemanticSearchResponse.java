package com.nhnacademy.blog.search.presentation.dto;

import com.nhnacademy.blog.post.presentation.dto.PostSummaryResponse;

/**
 * 의미 검색 결과 한 줄 `{ post: PostSummary, similarity }` (contracts SRCH, OWN-07).
 * similarity는 소수 둘째 자리까지(화면이 "가까움 0.58"로 보인다. 더 자세한 값은 뜻이 없다).
 */
public record SemanticSearchResponse(PostSummaryResponse post, double similarity) {

    public static SemanticSearchResponse of(PostSummaryResponse post, double similarity) {
        return new SemanticSearchResponse(post, Math.round(similarity * 100) / 100.0);
    }

}
