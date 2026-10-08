package com.nhnacademy.blog.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.FieldErrorDetail;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageQueryTest {

    @Test
    void defaultsToFirstPageAndListSize() {
        PageQuery query = PageQuery.of(null, null, 10);

        assertThat(query).isEqualTo(new PageQuery(1, 10));
        Pageable pageable = query.toPageable(Sort.by("id"));
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void pageStartsAtOne() {
        assertThat(PageQuery.of(3, 20, 10).toPageable(Sort.unsorted()).getPageNumber()).isEqualTo(2);
    }

    @Test
    void rejectsPageBelowOneAndSizeOutOfRange() {
        assertThatThrownBy(() -> PageQuery.of(0, 51, 10))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getFieldErrors()).extracting(FieldErrorDetail::field).containsExactly("page", "size");
                });
        assertThatThrownBy(() -> PageQuery.of(1, 0, 10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PageQuery.of(-1, null, 10)).isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptsSizeBounds() {
        assertThat(PageQuery.of(1, 1, 10).size()).isEqualTo(1);
        assertThat(PageQuery.of(1, 50, 10).size()).isEqualTo(50);
    }

    @Test
    void pageResponseIsOneBased() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5), String::toUpperCase);

        assertThat(response).isEqualTo(new PageResponse<>(List.of("A", "B"), 2, 2, 5, 3));
    }

}
