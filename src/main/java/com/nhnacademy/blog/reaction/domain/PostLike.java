package com.nhnacademy.blog.reaction.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 공감 (SOC-01). 회원 한 명이 글 하나에 하나(UNIQUE member_id, post_id). 공감 시각은 인기 점수(스텝 8)에 쓴다.
 * 넣고 지우기는 연타·동시 요청에도 하나가 되도록 PostLikeRepository의 한 문장 SQL로 한다.
 */
@Entity
@Table(name = "post_like")
public class PostLike extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    protected PostLike() {
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public Member getMember() {
        return member;
    }

}
