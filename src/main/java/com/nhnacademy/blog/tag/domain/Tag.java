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
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 블로그 태그 (TAG-01). 블로그 안에서 이름이 하나다(UNIQUE blog_id, name — DB 정렬 규칙이라 대소문자를 구분하지 않는다).
 * created_at이 없는 테이블이라 공통 부모를 쓰지 않는다.
 * <p>
 * @BatchSize: 글의 태그 이름을 읽을 때(PostTag → Tag 지연 로딩) 태그마다 SELECT를 하나씩 보내지 않고,
 * 아직 안 읽은 태그를 10개까지 모아 WHERE id IN (...) 한 번으로 읽는다(글당 태그는 10개까지).
 */
@BatchSize(size = 10)
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

    /** 이름 바꾸기 (TAG-04). 같은 블로그의 다른 태그와 겹치는지는 TagManageService가 먼저 본다. */
    public void rename(String name) {
        this.name = name;
    }

    public boolean belongsTo(Blog blog) {
        return this.blog.getId().equals(blog.getId());
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
