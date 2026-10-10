package com.nhnacademy.blog.comment.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {

    /** 글의 댓글 수(지운 것 빼고). 댓글 목록 머리의 "댓글 N"에 쓴다. */
    long countByPostIdAndDeletedAtIsNull(Long postId);

    /** 지울 댓글. 글이 어느 블로그인지 판단하려고 글과 블로그, 블로그 주인을 함께 읽는다. */
    @Query("select c from Comment c join fetch c.post p join fetch p.blog b join fetch b.member where c.id = :id")
    Optional<Comment> findWithPostById(@Param("id") Long id);

    /** 관리자 숨김·해제 (ADMIN-03). 작성자가 고친 것이 아니라 수정 시각("수정됨")은 그대로 둔다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Comment c set c.blinded = :blinded, c.updatedAt = c.updatedAt where c.id = :id")
    int changeBlinded(@Param("id") Long id, @Param("blinded") boolean blinded);

    /** 글을 지우면 그 글의 댓글도 소프트 삭제한다 (POST-03). 댓글 작성자가 고친 것이 아니라 수정 시각은 그대로 둔다. */
    @Modifying(clearAutomatically = true)
    @Query("update Comment c set c.deletedAt = :now, c.updatedAt = c.updatedAt"
            + " where c.post.id = :postId and c.deletedAt is null")
    int softDeleteByPostId(@Param("postId") Long postId, @Param("now") LocalDateTime now);

}
