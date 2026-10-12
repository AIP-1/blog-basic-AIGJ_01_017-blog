package com.nhnacademy.blog.auth.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원에 연결된 소셜 계정 (AUTH-01 소셜, OWN-03, R-07). 제공사의 회원 번호만 두고 제공사 이메일은 저장하지 않는다.
 * 소셜 계정 하나는 회원 하나에만 UNIQUE(provider, provider_user_id), 회원은 제공사마다 하나 UNIQUE(member_id, provider).
 * created_at이 연동한 때다.
 */
@Entity
@Table(name = "social_account")
public class SocialAccount extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 10)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    protected SocialAccount() {
    }

    private SocialAccount(Long memberId, SocialProvider provider, String providerUserId) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static SocialAccount link(Long memberId, SocialProvider provider, String providerUserId) {
        return new SocialAccount(memberId, provider, providerUserId);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

}
