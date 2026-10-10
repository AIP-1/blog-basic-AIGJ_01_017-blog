package com.nhnacademy.blog.blog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogSidebarModuleRepository extends JpaRepository<BlogSidebarModule, Long> {

    List<BlogSidebarModule> findByBlogIdOrderBySortOrderAscIdAsc(Long blogId);

}
