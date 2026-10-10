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

    /** 한 회원이 가질 수 있는 활성(삭제되지 않은) 블로그 수 (BLOG-01). */
    public static final int MAX_ACTIVE_PER_MEMBER = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이사 대상 블로그. 연쇄 이사 시 최종 대상으로 갱신한다 (BLOG-06). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moved_to_blog_id")
    private Blog movedToBlog;

    /** image.id. 주인이 올린 이미지만 된다(BlogService). 화면에는 그 이미지의 썸네일을 보인다. */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "skin", nullable = false, length = 20)
    private Skin skin;

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
        this.skin = Skin.BASIC;
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

    /** 꾸미기 (BLOG-05): 스킨, 메인 글 목록 형태, 포인트 색. null인 것은 그대로 둔다. */
    public void changeDesign(Skin skin, ListLayout listLayout, AccentColor accentColor) {
        if (skin != null) {
            this.skin = skin;
        }
        if (listLayout != null) {
            this.listLayout = listLayout;
        }
        if (accentColor != null) {
            this.accentColor = accentColor;
        }
    }

    /** 프로필 이미지 바꾸기 (BLOG-02). 주인이 올린 이미지인지는 BlogService가 먼저 본다. */
    public void changeProfileImage(Long profileImageId) {
        this.profileImageId = profileImageId;
    }

    /**
     * 대표 블로그 표시를 켜고 끈다 (BLOG-08). 회원마다 대표는 하나라 DB가 UNIQUE(primary_owner_id)로 지킨다.
     * 바꿀 때는 옛 대표를 먼저 끄고 DB에 반영(flush)한 뒤 새 대표를 켜야 제약에 걸리지 않는다(MyBlogService).
     */
    public void markPrimary(boolean primary) {
        this.primary = primary;
    }

    /** 이사 대상 정하기 (BLOG-06). 연쇄 이사면 부르는 쪽이 최종 블로그를 넘긴다. */
    public void moveTo(Blog target) {
        this.movedToBlog = target;
    }

    public void cancelMove() {
        this.movedToBlog = null;
    }

    /** 블로그 삭제 (BLOG-07). 행은 남아 주소가 영구 예약되고, 이사 연결(moved_to_blog_id)도 그대로다. */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    /** 관리자 이용 제한·해제 (ADMIN-05). 회원은 두고 이 블로그만 숨긴다. 사유는 moderation_log의 최신 RESTRICT_BLOG 행. */
    public void changeRestricted(boolean restricted) {
        this.restricted = restricted;
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

    public Skin getSkin() {
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
