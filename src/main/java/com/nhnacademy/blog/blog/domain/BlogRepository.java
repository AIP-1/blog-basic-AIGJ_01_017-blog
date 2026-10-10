package com.nhnacademy.blog.blog.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogRepository extends JpaRepository<Blog, Long>, JpaSpecificationExecutor<Blog> {

    /** 주소로 찾는다. 삭제된 블로그도 나온다(주소는 영구 예약). 주인과 이사 대상을 함께 읽는다. */
    @Query("select b from Blog b join fetch b.member left join fetch b.movedToBlog where b.address = :address")
    Optional<Blog> findByAddress(@Param("address") String address);

    /** 주소가 쓰였는가. 삭제된 블로그의 주소도 영구 예약이라 true다. */
    boolean existsByAddress(String address);

    /** 회원의 활성(삭제되지 않은) 블로그 수. */
    @Query("select count(b) from Blog b where b.member.id = :memberId and b.deletedAt is null")
    long countActiveByMemberId(@Param("memberId") Long memberId);

    /** 회원의 활성 블로그, 만든 순서. 이사 대상도 함께 읽는다(마이페이지 내 블로그, BLOG-08). */
    @Query("select b from Blog b left join fetch b.movedToBlog"
            + " where b.member.id = :memberId and b.deletedAt is null order by b.id")
    List<Blog> findActiveByMemberId(@Param("memberId") Long memberId);

    /** 회원의 대표 블로그(삭제되지 않은 것). */
    @Query("select b from Blog b where b.member.id = :memberId and b.primary = true and b.deletedAt is null")
    Optional<Blog> findPrimaryByMemberId(@Param("memberId") Long memberId);

    /** 여러 회원의 대표 블로그를 한 번에(댓글 작성자 목록 등). 볼 수 있는지 판단하려고 주인을 함께 읽는다. */
    @Query("select b from Blog b join fetch b.member"
            + " where b.member.id in :memberIds and b.primary = true and b.deletedAt is null")
    List<Blog> findPrimaryByMemberIds(@Param("memberIds") Collection<Long> memberIds);

    /** 이 블로그를 이사 대상으로 둔 블로그들을 새 최종 블로그로 바꾼다(연쇄 이사는 한 번에 최종 블로그로, BLOG-06). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Blog b set b.movedToBlog = :target where b.movedToBlog.id = :blogId")
    int retarget(@Param("blogId") Long blogId, @Param("target") Blog target);

    /** 회원의 블로그 전부(지운 것 포함), 만든 순서 (관리자 회원 상세). */
    List<Blog> findByMemberIdOrderById(Long memberId);

    /** 주인을 함께 읽는 블로그 하나(구독처럼 주소가 아니라 번호로 블로그를 가리키는 API). */
    @Query("select b from Blog b join fetch b.member where b.id = :id")
    Optional<Blog> findWithMemberById(@Param("id") Long id);

}
