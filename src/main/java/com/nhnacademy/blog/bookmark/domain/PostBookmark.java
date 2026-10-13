package com.nhnacademy.blog.bookmark.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 저장한 글 (T074, SOC-03). 회원·글마다 하나(UNIQUE member_id, post_id). 저장할 때의 제목과 블로그 이름을 남겨,
 * 나중에 글을 볼 수 없게 되어도 목록에 "볼 수 없는 글"로 그 이름만 보여 준다(헌법 원칙 II의 정해진 예외).
 * 넣기는 ON CONFLICT DO NOTHING(PostBookmarkRepository)으로 해서 이 생성자는 읽을 때만 쓴다.
 */
@Entity
@Table(name = "post_bookmark")
public class PostBookmark extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "title_snapshot", nullable = false, length = 200)
    private String titleSnapshot;

    @Column(name = "blog_name_snapshot", nullable = false, length = 50)
    private String blogNameSnapshot;

    protected PostBookmark() {
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getTitleSnapshot() {
        return titleSnapshot;
    }

    public String getBlogNameSnapshot() {
        return blogNameSnapshot;
    }

}
