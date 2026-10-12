package com.nhnacademy.blog.manage.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogBlockedMemberRepository extends JpaRepository<BlogBlockedMember, Long> {

    /** 차단. 이미 차단했으면(UNIQUE) 0. */
    @Modifying
    @Query(value = "INSERT IGNORE INTO blog_blocked_member (blog_id, blocked_member_id, memo) VALUES (:blogId, :memberId, :memo)",
            nativeQuery = true)
    int insertIfAbsent(@Param("blogId") Long blogId, @Param("memberId") Long memberId, @Param("memo") String memo);

    @Modifying
    @Query(value = "DELETE FROM blog_blocked_member WHERE blog_id = :blogId AND blocked_member_id = :memberId",
            nativeQuery = true)
    int deleteIfPresent(@Param("blogId") Long blogId, @Param("memberId") Long memberId);

    boolean existsByBlogIdAndBlockedMemberId(Long blogId, Long blockedMemberId);

    Page<BlogBlockedMember> findByBlogId(Long blogId, Pageable pageable);

}
