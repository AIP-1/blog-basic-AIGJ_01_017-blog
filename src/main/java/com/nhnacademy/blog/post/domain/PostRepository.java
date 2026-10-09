package com.nhnacademy.blog.post.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post>,
        PostCountRepository {

    /** 가시성 판단에 필요한 블로그와 블로그 주인, 글 상세에 쓰는 카테고리를 함께 읽는다. 삭제된 글도 나온다. */
    @Query("select p from Post p join fetch p.blog b join fetch b.member left join fetch p.category where p.id = :id")
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

    /** 글을 지우면 그 글의 공감도 지운다 (POST-03). 공감 엔티티는 스텝 7에서 만들므로 SQL로 지운다. */
    @Modifying
    @Query(value = "DELETE FROM post_like WHERE post_id = :postId", nativeQuery = true)
    int deleteLikes(@Param("postId") Long postId);

    /** 글과 그 글의 댓글을 가리키는 알림을 지운다 (POST-03). 알림 기능(SUB-04)보다 먼저 삭제 규칙을 맞춰 둔다. */
    @Modifying
    @Query(value = "DELETE FROM notification WHERE (target_type = 'POST' AND target_id = :postId)"
            + " OR (target_type = 'COMMENT' AND target_id IN (SELECT id FROM comment WHERE post_id = :postId))",
            nativeQuery = true)
    int deleteNotifications(@Param("postId") Long postId);

    /**
     * 댓글 수 늘리기·줄이기 (CMT-01). 한 줄 UPDATE라 동시에 여러 댓글이 달려도 값이 어긋나지 않는다.
     * 작성자가 글을 고친 것이 아니라서 updated_at은 그대로 둔다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + :delta, p.updatedAt = p.updatedAt where p.id = :postId")
    int addCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    /** 조회수 하나 올리기 (POST-09). 댓글 수와 같은 한 줄 UPDATE이고, 조회는 글을 고친 것이 아니라 updated_at을 그대로 둔다. */
    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1, p.updatedAt = p.updatedAt where p.id = :postId")
    int increaseViewCount(@Param("postId") Long postId);

    /** 지우지 않은 발행 글 번호 전부. 추천 임베딩을 서버가 뜰 때 채우는 데 쓴다 (T069b). */
    @Query("select p.id from Post p where p.status = com.nhnacademy.blog.post.domain.PostStatus.PUBLISHED"
            + " and p.deletedAt is null")
    List<Long> findPublishedIds();

    /** 공감 수 늘리기·줄이기 (SOC-01). 댓글 수와 같은 방식이다. */
    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + :delta, p.updatedAt = p.updatedAt where p.id = :postId")
    int addLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    /**
     * 공감을 켜고 끈 뒤 응답에 넣을 지금 공감 수. 잠그며 읽어야(FOR UPDATE) 다른 트랜잭션이 막 커밋한 값이 보인다.
     * 평범한 SELECT는 이 트랜잭션이 처음 읽은 때의 스냅샷(REPEATABLE READ)을 보여 줘서, 잠금을 기다린 요청은 옛 수를 돌려준다.
     * lockById로 이미 잠근 행이라 더 기다리지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p.likeCount from Post p where p.id = :postId")
    int findLikeCount(@Param("postId") Long postId);

    /**
     * 글 행을 잠근다(SELECT ... FOR UPDATE). 공감·댓글을 넣고 수를 고치는 트랜잭션이 맨 먼저 부른다.
     * 공감·댓글 INSERT는 외래 키 확인 때문에 글 행에 공유 잠금을 걸고, 뒤의 수 UPDATE는 배타 잠금이 필요하다.
     * 두 트랜잭션이 공유 잠금을 쥔 채 서로 배타 잠금을 기다리면 데드락이 난다. 처음부터 배타 잠금을 잡아 차례로 줄 세운다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p.id from Post p where p.id = :postId")
    Optional<Long> lockById(@Param("postId") Long postId);

}
