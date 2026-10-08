package com.nhnacademy.blog.tag.domain;

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
 * 블로그 태그 (TAG-01). 블로그 안에서 이름이 하나다(UNIQUE blog_id, name — DB 정렬 규칙이라 대소문자를 구분하지 않는다).
 * created_at이 없는 테이블이라 공통 부모를 쓰지 않는다.
 */
@Entity
@Table(name = "tag")
@EntityListeners(AuditingEntityListener.class)
public class Tag {

    public static final int MAX_NAME_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Tag() {
    }

    private Tag(Blog blog, String name) {
        this.blog = blog;
        this.name = name;
    }

    public static Tag create(Blog blog, String name) {
        return new Tag(blog, name);
    }

    public Long getId() {
        return id;
    }

    public Blog getBlog() {
        return blog;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
