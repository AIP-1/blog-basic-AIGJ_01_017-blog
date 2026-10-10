package com.nhnacademy.blog.subscription.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByMemberIdAndBlogId(Long memberId, Long blogId);

    long countByBlogId(Long blogId);

    /**
     * 구독 넣기 (SUB-01). 이미 있으면 넣지 않는다. 넣은 행 수(0 또는 1)라 새로 구독했는지 알 수 있다(알림은 새 구독에만).
     * "있나 보고 넣기"를 두 번에 나누면 연타한 두 요청이 둘 다 넣으려다 하나가 UNIQUE 오류가 난다(공감과 같은 방법).
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO subscription (member_id, blog_id) VALUES (:memberId, :blogId)", nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("blogId") Long blogId);

    /** 구독 지우기. 지운 행 수(0 또는 1). */
    @Modifying
    @Query(value = "DELETE FROM subscription WHERE member_id = :memberId AND blog_id = :blogId", nativeQuery = true)
    int deleteIfPresent(@Param("memberId") Long memberId, @Param("blogId") Long blogId);

    /** 블로그마다 구독자 수 [블로그 id, 수]. 구독자가 없는 블로그는 결과에 없다. 검색 결과 한 페이지를 한 번에 센다. */
    @Query("select s.blog.id, count(s) from Subscription s where s.blog.id in :blogIds group by s.blog.id")
    List<Object[]> countByBlogIds(@Param("blogIds") Collection<Long> blogIds);

}
