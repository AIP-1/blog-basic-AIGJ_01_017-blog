package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.post.domain.Visibility;
import jakarta.validation.constraints.NotNull;

/** 공개 범위 변경 (POST-06). 구독자 공개는 구독(SUB-01) 뒤에 연다. */
public record VisibilityRequest(
        @NotNull(message = "공개 범위를 골라 주세요.")
        Visibility visibility) {

    public void checkSupported() {
        if (visibility == Visibility.SUBSCRIBERS) {
            throw BusinessException.invalidField("visibility", "구독자 공개는 아직 고를 수 없습니다.");
        }
    }

}
