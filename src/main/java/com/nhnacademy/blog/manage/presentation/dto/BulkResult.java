package com.nhnacademy.blog.manage.presentation.dto;

/** 일괄 처리 결과. 공개 범위 변경은 `{ updatedCount }`, 삭제는 `{ deletedCount }` (contracts MNG-01). */
public final class BulkResult {

    private BulkResult() {
    }

    public record Updated(int updatedCount) {
    }

    public record Deleted(int deletedCount) {
    }

}
