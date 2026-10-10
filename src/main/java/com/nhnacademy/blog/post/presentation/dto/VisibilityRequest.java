package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.post.domain.Visibility;
import jakarta.validation.constraints.NotNull;

/** 공개 범위 변경 (POST-06). 구독자 공개(POST-12)는 구독(스텝 16)이 생겨 고를 수 있다. */
public record VisibilityRequest(
        @NotNull(message = "공개 범위를 골라 주세요.")
        Visibility visibility) {
}
