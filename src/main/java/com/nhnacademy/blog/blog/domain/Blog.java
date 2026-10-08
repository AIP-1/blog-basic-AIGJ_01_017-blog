package com.nhnacademy.blog.blog.domain;

import com.nhnacademy.blog.global.entity.BaseTimeEntity;
import com.nhnacademy.blog.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 블로그. 주소는 불변이고, 삭제돼도 행이 남아 주소가 영구 예약된다.
 * 계산 컬럼 primary_owner_id(대표 블로그 유니크용)는 DB가 채우므로 매핑하지 않는다.
 */
@Entity
@Table(name = "blog")
public class Blog extends BaseTimeEntity {

    public static final String DEFAULT_SKIN = "BASIC";
    /** 한 회원이 가질 수 있는 활성(삭제되지 않은) 블로그 수 (BLOG-01). */
    public static final int MAX_ACTIVE_PER_MEMBER = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이사 대상 블로그. 연쇄 이사 시 최종 대상으로 갱신한다 (BLOG-06). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moved_to_blog_id")
    private Blog movedToBlog;

    /** image.id. 이미지 엔티티는 스텝 7에서 만든다. */
    @Column(name = "profile_image_id")
    private Long profileImageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "address", nullable = false, updatable = false, length = 32)
    private String address;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "skin", nullable = false, length = 20)
    private String skin;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_layout", nullable = false, length = 10)
    private ListLayout listLayout;

    @Column(name = "is_restricted", nullable = false)
    private boolean restricted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "total_visitor_count", nullable = false)
    private long totalVisitorCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "accent_color", nullable = false, length = 10)
    private AccentColor accentColor;

    protected Blog() {
    }

    private Blog(Member member, String address, String name, String description, boolean primary) {
        this.member = member;
        this.address = address;
        this.name = name;
        this.description = description;
        this.primary = primary;
        this.skin = DEFAULT_SKIN;
        this.listLayout = ListLayout.LIST;
        this.accentColor = AccentColor.BLUE;
    }

    /** 블로그 개설 (BLOG-01). 회원의 첫 블로그면 대표 블로그다. */
    public static Blog open(Member member, String address, String name, boolean primary) {
        return open(member, address, name, null, primary);
    }

    public static Blog open(Member member, String address, String name, String description, boolean primary) {
        return new Blog(member, address, name, description, primary);
    }

    /** 이름·소개 수정 (BLOG-02). null이면 그대로 둔다. 주소는 바꿀 수 없다. */
    public void changeInfo(String name, String description) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description.isBlank() ? null : description;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isMoved() {
        return movedToBlog != null;
    }

    public boolean isOwnedBy(Long memberId) {
        return memberId != null && memberId.equals(member.getId());
    }

    public Long getId() {
        return id;
    }

    public Blog getMovedToBlog() {
        return movedToBlog;
    }

    public Long getProfileImageId() {
        return profileImageId;
    }

    public Member getMember() {
        return member;
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getSkin() {
        return skin;
    }

    public ListLayout getListLayout() {
        return listLayout;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public long getTotalVisitorCount() {
        return totalVisitorCount;
    }

    public AccentColor getAccentColor() {
        return accentColor;
    }

}
