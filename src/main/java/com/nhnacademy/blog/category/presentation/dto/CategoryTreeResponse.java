package com.nhnacademy.blog.category.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nhnacademy.blog.category.application.CategoryTree;
import java.util.List;

/**
 * 카테고리 트리 응답 (contracts/rest-api.md CAT). 사이드바 CATEGORY 모듈과 GET /api/categories(스텝 5)가 같이 쓴다.
 * isPrivate는 주인에게만 준다.
 */
public record CategoryTreeResponse(long totalCount, long uncategorizedCount, List<Node> categories) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Node(Long id, String name, Boolean isPrivate, long postCount, int sortOrder, List<Node> children) {
    }

    public static CategoryTreeResponse from(CategoryTree tree) {
        return new CategoryTreeResponse(tree.totalCount(), tree.uncategorizedCount(),
                tree.categories().stream().map(node -> toNode(node, tree.ownerView())).toList());
    }

    private static Node toNode(CategoryTree.Node node, boolean ownerView) {
        return new Node(node.id(), node.name(), ownerView ? node.privateCategory() : null, node.postCount(),
                node.sortOrder(), node.children().stream().map(child -> toNode(child, ownerView)).toList());
    }

}
