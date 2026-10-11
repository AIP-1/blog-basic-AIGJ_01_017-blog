package com.nhnacademy.blog.manage.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 블로그 주인이 차단한 회원 (T084, MNG-04). 블로그·회원마다 하나(UNIQUE). 차단된 회원은 그 블로그에 댓글·방명록을 쓰거나 구독할 수 없다.
 * 주인이 쓰는 기능이라 서비스 관리 이력에는 남기지 않는다. 넣기는 INSERT IGNORE(저장소)로 해서 이 생성자는 읽을 때만 쓴다.
 */
@Entity
@Table(name = "blog_blocked_member")
public class BlogBlockedMember extends BaseCreatedEntity {

    public static final int MEMO_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "blocked_member_id", nullable = false)
    private Long blockedMemberId;

    @Column(name = "memo", length = MEMO_LENGTH)
    private String memo;

    protected BlogBlockedMember() {
    }

    public Long getId() {
        return id;
    }

    public Long getBlogId() {
        return blogId;
    }

    public Long getBlockedMemberId() {
        return blockedMemberId;
    }

    public String getMemo() {
        return memo;
    }

}
