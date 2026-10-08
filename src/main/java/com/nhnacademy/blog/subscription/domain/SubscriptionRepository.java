package com.nhnacademy.blog.subscription.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByMemberIdAndBlogId(Long memberId, Long blogId);

    long countByBlogId(Long blogId);

}
