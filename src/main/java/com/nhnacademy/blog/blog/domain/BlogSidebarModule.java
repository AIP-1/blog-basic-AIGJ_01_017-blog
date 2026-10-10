package com.nhnacademy.blog.blog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 블로그 사이드바의 모듈 하나 (T086, BLOG-05). 블로그마다 종류별로 한 행(UNIQUE blog_id, module_type)이고,
 * 순서(sort_order)와 표시 여부(is_visible)를 주인이 바꾼다. created_at이 없는 테이블이라 공통 부모를 쓰지 않는다.
 */
@Entity
@Table(name = "blog_sidebar_module")
@EntityListeners(AuditingEntityListener.class)
public class BlogSidebarModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 20)
    private SidebarModuleType moduleType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_visible", nullable = false)
    private boolean visible;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BlogSidebarModule() {
    }

    private BlogSidebarModule(Long blogId, SidebarModuleType moduleType, int sortOrder, boolean visible) {
        this.blogId = blogId;
        this.moduleType = moduleType;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    public static BlogSidebarModule of(Long blogId, SidebarModuleType moduleType, int sortOrder, boolean visible) {
        return new BlogSidebarModule(blogId, moduleType, sortOrder, visible);
    }

    public void place(int sortOrder, boolean visible) {
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    public Long getId() {
        return id;
    }

    public Long getBlogId() {
        return blogId;
    }

    public SidebarModuleType getModuleType() {
        return moduleType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isVisible() {
        return visible;
    }

}
