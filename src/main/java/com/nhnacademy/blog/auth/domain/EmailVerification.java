package com.nhnacademy.blog.auth.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 이메일 인증 코드 (OWN-01). 가입 전 단계라 회원과 연결하지 않고 이메일 값으로 찾는다.
 * 같은 이메일로 여러 번 받으면 가장 최근 것만 쓴다. 가입하면 verified_at을 남겨 다시 쓰지 못하게 한다.
 */
@Entity
@Table(name = "email_verification")
public class EmailVerification extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    protected EmailVerification() {
    }

    private EmailVerification(String email, String code, LocalDateTime expiresAt) {
        this.email = email;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public static EmailVerification issue(String email, String code, LocalDateTime expiresAt) {
        return new EmailVerification(email, code, expiresAt);
    }

    public boolean matches(String inputCode) {
        return code.equals(inputCode);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 이미 가입에 쓴 코드인가. */
    public boolean isUsed() {
        return verifiedAt != null;
    }

    /** 가입에 썼다고 남긴다. */
    public void markVerified(LocalDateTime now) {
        this.verifiedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

}
