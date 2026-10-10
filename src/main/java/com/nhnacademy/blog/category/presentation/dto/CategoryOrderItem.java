package com.nhnacademy.blog.category.presentation.dto;

/**
 * 순서 바꾸기 한 줄 `{ id, parentId, sortOrder }` (CAT-04). 끌어서 놓은 결과 전체를 한 번에 보낸다.
 * parentId가 null이면 최상위. sortOrder는 같은 자리 안의 순서(작을수록 위).
 */
public record CategoryOrderItem(Long id, Long parentId, Integer sortOrder) {
}
