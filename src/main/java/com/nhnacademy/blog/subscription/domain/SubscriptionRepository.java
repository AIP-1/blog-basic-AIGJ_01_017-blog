package com.nhnacademy.blog.subscription.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByMemberIdAndBlogId(Long memberId, Long blogId);

    long countByBlogId(Long blogId);

    /** 블로그마다 구독자 수 [블로그 id, 수]. 구독자가 없는 블로그는 결과에 없다. 검색 결과 한 페이지를 한 번에 센다. */
    @Query("select s.blog.id, count(s) from Subscription s where s.blog.id in :blogIds group by s.blog.id")
    List<Object[]> countByBlogIds(@Param("blogIds") Collection<Long> blogIds);

}
