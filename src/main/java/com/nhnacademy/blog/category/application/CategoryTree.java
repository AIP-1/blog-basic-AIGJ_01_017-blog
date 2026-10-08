package com.nhnacademy.blog.category.application;

import java.util.List;

/**
 * 카테고리 트리와 글 수 (CAT-01, CAT-02, BLOG-04). '전체 글'과 '미분류'는 행이 아니라 숫자로만 있다.
 *
 * @param totalCount         보는 사람이 볼 수 있는 글 전체 수
 * @param uncategorizedCount 미분류 글 수. 0이면 화면에서 미분류를 숨긴다
 * @param ownerView          주인이 보는 트리인가. 주인에게만 비공개 여부를 보여 준다
 */
public record CategoryTree(long totalCount, long uncategorizedCount, List<Node> categories, boolean ownerView) {

    /** postCount는 하위 카테고리 글을 포함한 수다. */
    public record Node(Long id, String name, boolean privateCategory, long postCount, int sortOrder,
                       List<Node> children) {
    }

}
