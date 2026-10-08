package com.nhnacademy.blog.subscription.domain;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import com.nhnacademy.blog.member.domain.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 구독. UNIQUE(member_id, blog_id)로 중복을 막는다.
 */
@Entity
@Table(name = "subscription")
public class Subscription extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    protected Subscription() {
    }

    private Subscription(Member member, Blog blog) {
        this.member = member;
        this.blog = blog;
    }

    public static Subscription subscribe(Member member, Blog blog) {
        return new Subscription(member, blog);
    }

    public Long getId() {
        return id;
    }

    public Blog getBlog() {
        return blog;
    }

    public Member getMember() {
        return member;
    }

}
