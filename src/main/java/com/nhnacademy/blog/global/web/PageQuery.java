package com.nhnacademy.blog.global.web;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 페이지 번호 목록 요청 (contracts/rest-api.md 목록). page는 1부터, size는 1~50.
 * 생략하면 page 1, size는 목록마다 정한 기본값이다. 범위를 벗어나면 400 VALIDATION_FAILED.
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 50;

    public static PageQuery of(Integer page, Integer size, int defaultSize) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (page != null && page < 1) {
            errors.add(new FieldErrorDetail("page", "페이지는 1부터입니다."));
        }
        if (size != null && (size < 1 || size > MAX_SIZE)) {
            errors.add(new FieldErrorDetail("size", "한 번에 1~" + MAX_SIZE + "개까지 볼 수 있습니다."));
        }
        if (!errors.isEmpty()) {
            throw BusinessException.fieldErrors(ErrorCode.VALIDATION_FAILED, errors);
        }
        return new PageQuery(page == null ? 1 : page, size == null ? defaultSize : size);
    }

    /** Spring Data는 0부터 세므로 하나 뺀다. */
    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page - 1, size, sort);
    }

}
