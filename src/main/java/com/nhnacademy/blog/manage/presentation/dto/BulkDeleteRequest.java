package com.nhnacademy.blog.manage.presentation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 일괄 삭제 `{ postIds }` (MNG-01). 한 번에 100개까지. */
public record BulkDeleteRequest(
        @NotEmpty(message = "글을 골라 주세요.")
        @Size(max = 100, message = "한 번에 100개까지 지울 수 있습니다.")
        List<Long> postIds) {
}
