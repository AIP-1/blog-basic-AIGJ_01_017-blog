package com.nhnacademy.blog.category.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 블로그의 카테고리 전부, 주인이 정한 순서대로. */
    List<Category> findByBlogIdOrderBySortOrderAscIdAsc(Long blogId);

    /** 같은 블로그의 최상위 카테고리 중 같은 이름이 있는가. 대소문자는 DB 정렬 규칙(ai_ci)대로 같게 본다. */
    boolean existsByBlogIdAndParentIsNullAndName(Long blogId, String name);

    boolean existsByParentId(Long parentId);

    /** 새 최상위 카테고리를 맨 아래에 두기 위한 지금 가장 큰 순서. 없으면 -1. */
    @Query("select coalesce(max(c.sortOrder), -1) from Category c where c.blog.id = :blogId and c.parent is null")
    int findMaxRootSortOrder(@Param("blogId") Long blogId);

}
