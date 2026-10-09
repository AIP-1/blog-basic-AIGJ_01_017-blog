package com.nhnacademy.blog.manage.presentation.dto;

import com.nhnacademy.blog.post.domain.Visibility;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 일괄 공개 범위 변경 `{ postIds, visibility }` (MNG-01). 한 번에 100개까지. */
public record BulkVisibilityRequest(
        @NotEmpty(message = "글을 골라 주세요.")
        @Size(max = 100, message = "한 번에 100개까지 바꿀 수 있습니다.")
        List<Long> postIds,

        @NotNull(message = "공개 범위를 골라 주세요.")
        Visibility visibility) {
}
