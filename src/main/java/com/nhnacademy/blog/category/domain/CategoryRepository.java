package com.nhnacademy.blog.category.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 블로그의 카테고리 전부, 주인이 정한 순서대로. */
    List<Category> findByBlogIdOrderBySortOrderAscIdAsc(Long blogId);

}
