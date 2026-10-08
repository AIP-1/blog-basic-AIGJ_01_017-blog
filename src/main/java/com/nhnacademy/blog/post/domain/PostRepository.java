package com.nhnacademy.blog.post.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** 가시성 판단에 필요한 블로그와 블로그 주인을 함께 읽는다. 삭제된 글도 나온다. */
    @Query("select p from Post p join fetch p.blog b join fetch b.member where p.id = :id")
    Optional<Post> findWithBlogById(@Param("id") Long id);

}
