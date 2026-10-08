package com.nhnacademy.blog.comment.domain;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {

    /** 글을 지우면 그 글의 댓글도 소프트 삭제한다 (POST-03). 댓글 작성자가 고친 것이 아니라 수정 시각은 그대로 둔다. */
    @Modifying(clearAutomatically = true)
    @Query("update Comment c set c.deletedAt = :now, c.updatedAt = c.updatedAt"
            + " where c.post.id = :postId and c.deletedAt is null")
    int softDeleteByPostId(@Param("postId") Long postId, @Param("now") LocalDateTime now);

}
