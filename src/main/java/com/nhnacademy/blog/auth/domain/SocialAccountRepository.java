package com.nhnacademy.blog.auth.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    Optional<SocialAccount> findByMemberIdAndProvider(Long memberId, SocialProvider provider);

    /** 마이페이지 목록(연동한 순서). */
    List<SocialAccount> findByMemberIdOrderByCreatedAtAscIdAsc(Long memberId);

    long countByMemberId(Long memberId);

}
