package com.nhnacademy.blog.comment.domain;

import com.nhnacademy.blog.global.entity.BaseTimeEntity;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
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
 * 댓글. 답글은 1단계까지다. 답글이 있는 댓글을 지우면 '삭제된 댓글입니다'로 보인다.
 * 방명록(Guestbook)과 같은 규칙으로 보이도록 CommentEntry를 따른다.
 */
@Entity
@Table(name = "comment")
public class Comment extends BaseTimeEntity implements CommentEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 부모 댓글. NULL 또는 1단계 (CMT-05). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @Column(name = "is_blinded", nullable = false)
    private boolean blinded;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Comment() {
    }

    private Comment(Post post, Member member, Comment parent, String content, boolean secret) {
        this.post = post;
        this.member = member;
        this.parent = parent;
        this.content = content;
        this.secret = secret;
    }

    public static Comment write(Post post, Member member, String content, boolean secret) {
        return new Comment(post, member, null, content, secret);
    }

    public static Comment reply(Comment parent, Member member, String content, boolean secret) {
        return new Comment(parent.getPost(), member, parent, content, secret);
    }

    /** 소프트 삭제 (CMT-01, CMT-02). 답글(CMT-05)이 생기면 답글이 있는 댓글은 '삭제된 댓글입니다'로 자리를 남긴다. */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    /** 내용 고치기 (CMT-03). 본인인지, 숨긴 댓글이 아닌지는 CommentService가 먼저 본다. */
    public void edit(String content) {
        this.content = content;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Comment getParent() {
        return parent;
    }

    @Override
    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }

    public Post getPost() {
        return post;
    }

    public Member getMember() {
        return member;
    }

    public String getContent() {
        return content;
    }

    public boolean isSecret() {
        return secret;
    }

    public boolean isBlinded() {
        return blinded;
    }

    /** 관리자 숨김·해제 (ADMIN-03). 사유는 moderation_log의 최신 BLIND 행에 있다. */
    public void changeBlinded(boolean blinded) {
        this.blinded = blinded;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

}
