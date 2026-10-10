package com.nhnacademy.blog.member.domain;

import com.nhnacademy.blog.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 회원. 이메일 가입·소셜 가입 회원. 탈퇴해도 행은 남는다.
 */
@Entity
@Table(name = "member")
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** image.id. 본인이 올린 이미지만 들어간다(MeService가 확인). */
    @Column(name = "profile_image_id")
    private Long profileImageId;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    protected Member() {
    }

    private Member(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = Role.USER;
        this.status = MemberStatus.ACTIVE;
    }

    /** 이메일 가입 회원. 가입은 항상 USER다. */
    public static Member ofEmail(String email, String passwordHash, String nickname) {
        return new Member(email, passwordHash, nickname);
    }

    /** 소셜 가입 회원. 이메일·비밀번호가 없다. */
    public static Member ofSocial(String nickname) {
        return new Member(null, null, nickname);
    }

    /** 지금 정지 중인가. 정지 종료 시각이 지났으면 정지가 아니다. 종료 시각이 없으면 영구 정지. */
    public boolean isSuspendedAt(LocalDateTime now) {
        return status == MemberStatus.SUSPENDED && (suspendedUntil == null || suspendedUntil.isAfter(now));
    }

    /**
     * 정지 (ADMIN-02). until이 null이면 영구 정지다. 기간이 지나면 isSuspendedAt이 false라 따로 풀지 않아도 풀린다.
     * 사유는 moderation_log의 최신 SUSPEND 행에 있다.
     */
    public void suspend(LocalDateTime until) {
        this.status = MemberStatus.SUSPENDED;
        this.suspendedUntil = until;
    }

    /** 정지 해제 (ADMIN-02). */
    public void unsuspend() {
        this.status = MemberStatus.ACTIVE;
        this.suspendedUntil = null;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** 회원정보 수정 (AUTH-05). 중복 검사는 MeService가 하고, 마지막 판단은 DB UNIQUE가 한다. */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeProfileImage(Long profileImageId) {
        this.profileImageId = profileImageId;
    }

    /** 이메일 가입 회원의 비밀번호 바꾸기. bcrypt 해시를 받는다. */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileImageId() {
        return profileImageId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
    }

    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

}
