package com.nhnacademy.blog.post.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post>,
        PostCountRepository {

    /** 가시성 판단에 필요한 블로그와 블로그 주인을 함께 읽는다. 삭제된 글도 나온다. */
    @Query("select p from Post p join fetch p.blog b join fetch b.member where p.id = :id")
    Optional<Post> findWithBlogById(@Param("id") Long id);

    /**
     * 글 목록(페이지). 목록 한 줄에 카테고리 이름이 나가므로 카테고리를 함께 읽는다(N+1 방지).
     * 개수 쿼리에는 이 fetch가 붙지 않는다.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Post> findAll(Specification<Post> spec, Pageable pageable);

    /**
     * 카테고리를 지울 때 그 카테고리의 글을 미분류로 옮긴다 (CAT-01). 삭제된 글도 함께 옮겨 외래 키가 남지 않게 한다.
     * updated_at은 MySQL ON UPDATE로 바뀌지 않게 그대로 다시 넣는다. 작성자가 고친 것이 아니라서 수정 시각을 남기지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.category = null, p.updatedAt = p.updatedAt where p.category.id = :categoryId")
    int uncategorize(@Param("categoryId") Long categoryId);

}
