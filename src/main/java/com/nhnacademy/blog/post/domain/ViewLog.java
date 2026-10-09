package com.nhnacademy.blog.post.domain;

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
 * 조회 기록 (POST-09). 조회수로 센 조회마다 한 행이다. 같은 조회자가 5분 안에 다시 열면 남기지 않는다.
 * 최근 1시간 인기 점수(HOME-02)가 이 행 수를 센다. 조회 시각은 테스트에서 바꿀 수 있게 Clock으로 넣는다.
 */
@Entity
@Table(name = "view_log")
public class ViewLog {

    public static final int MAX_VIEWER_KEY_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** 회원이면 "m:{회원 id}", 비회원이면 "a:{방문자 쿠키 값}" (VisitorKeys). */
    @Column(name = "viewer_key", nullable = false, length = MAX_VIEWER_KEY_LENGTH)
    private String viewerKey;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    protected ViewLog() {
    }

    private ViewLog(Post post, String viewerKey, LocalDateTime viewedAt) {
        this.post = post;
        this.viewerKey = viewerKey;
        this.viewedAt = viewedAt;
    }

    public static ViewLog of(Post post, String viewerKey, LocalDateTime viewedAt) {
        return new ViewLog(post, viewerKey, viewedAt);
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public String getViewerKey() {
        return viewerKey;
    }

    public LocalDateTime getViewedAt() {
        return viewedAt;
    }

}
