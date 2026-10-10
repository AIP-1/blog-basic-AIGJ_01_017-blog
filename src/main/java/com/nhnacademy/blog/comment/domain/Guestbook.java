package com.nhnacademy.blog.comment.domain;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.entity.BaseTimeEntity;
import com.nhnacademy.blog.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 방명록 (CMT-04). 글이 아니라 블로그에 달린다는 것 말고는 댓글과 같은 규칙이다: 1~1,000자, 비밀글은 블로그 주인과
 * 작성자만, 답글은 한 단계, 답글이 있는 글을 지우면 '삭제된 글입니다' 자리로 남는다. 관리자 숨김은 없다(ERD에 칸이 없음).
 */
@Entity
@Table(name = "guestbook")
public class Guestbook extends BaseTimeEntity implements CommentEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 부모 방명록. NULL 또는 1단계. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Guestbook parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Guestbook() {
    }

    private Guestbook(Blog blog, Member member, Guestbook parent, String content, boolean secret) {
        this.blog = blog;
        this.member = member;
        this.parent = parent;
        this.content = content;
        this.secret = secret;
    }

    public static Guestbook write(Blog blog, Member member, String content, boolean secret) {
        return new Guestbook(blog, member, null, content, secret);
    }

    public static Guestbook reply(Guestbook parent, Member member, String content, boolean secret) {
        return new Guestbook(parent.blog, member, parent, content, secret);
    }

    public void edit(String content) {
        this.content = content;
    }

    /** 소프트 삭제. 답글이 남아 있으면 목록에 자리만 남는다. */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Guestbook getParent() {
        return parent;
    }

    @Override
    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }

    public Blog getBlog() {
        return blog;
    }

    @Override
    public Member getMember() {
        return member;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public boolean isSecret() {
        return secret;
    }

    @Override
    public boolean isBlinded() {
        return false;
    }

    @Override
    public boolean isDeleted() {
        return deletedAt != null;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

}
