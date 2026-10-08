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

    /** image.id. 이미지 엔티티는 스텝 7에서 만든다. */
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
