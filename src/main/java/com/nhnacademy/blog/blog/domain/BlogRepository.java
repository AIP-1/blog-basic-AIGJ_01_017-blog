package com.nhnacademy.blog.blog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    /** 주소로 찾는다. 삭제된 블로그도 나온다(주소는 영구 예약). 주인과 이사 대상을 함께 읽는다. */
    @Query("select b from Blog b join fetch b.member left join fetch b.movedToBlog where b.address = :address")
    Optional<Blog> findByAddress(@Param("address") String address);

    /** 주소가 쓰였는가. 삭제된 블로그의 주소도 영구 예약이라 true다. */
    boolean existsByAddress(String address);

    /** 회원의 활성(삭제되지 않은) 블로그 수. */
    @Query("select count(b) from Blog b where b.member.id = :memberId and b.deletedAt is null")
    long countActiveByMemberId(@Param("memberId") Long memberId);

    /** 회원의 대표 블로그(삭제되지 않은 것). */
    @Query("select b from Blog b where b.member.id = :memberId and b.primary = true and b.deletedAt is null")
    Optional<Blog> findPrimaryByMemberId(@Param("memberId") Long memberId);

}
