package com.nhnacademy.blog.category.domain;

import com.nhnacademy.blog.blog.domain.Blog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 카테고리. '전체 글'·'미분류'는 행이 아니라 가상 항목이다.
 * 계산 컬럼 parent_key(최상위 이름 중복 방지용)는 DB가 채우므로 매핑하지 않는다.
 * created_at이 없는 테이블이라 공통 부모를 쓰지 않는다.
 */
@Entity
@Table(name = "category")
@EntityListeners(AuditingEntityListener.class)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상위 카테고리. NULL 또는 1단계 상위 (CAT-03). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_private", nullable = false)
    private boolean privateCategory;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Category() {
    }

    private Category(Blog blog, Category parent, String name, int sortOrder) {
        this.blog = blog;
        this.parent = parent;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public static Category create(Blog blog, Category parent, String name, int sortOrder) {
        return new Category(blog, parent, name, sortOrder);
    }

    /** 이 블로그의 카테고리인가. 다른 블로그의 카테고리 번호로 요청하면 없는 것과 같다. */
    /** 하위 카테고리인가. 2단계까지라 하위의 하위는 없다 (CAT-03). */
    public boolean isChild() {
        return parent != null;
    }

    public boolean belongsTo(Blog blog) {
        return this.blog.getId().equals(blog.getId());
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Category getParent() {
        return parent;
    }

    public Blog getBlog() {
        return blog;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isPrivateCategory() {
        return privateCategory;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
