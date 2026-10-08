package com.nhnacademy.blog.blog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    /** 주소로 찾는다. 삭제된 블로그도 나온다(주소는 영구 예약). 주인과 이사 대상을 함께 읽는다. */
    @Query("select b from Blog b join fetch b.member left join fetch b.movedToBlog where b.address = :address")
    Optional<Blog> findByAddress(@Param("address") String address);

}
