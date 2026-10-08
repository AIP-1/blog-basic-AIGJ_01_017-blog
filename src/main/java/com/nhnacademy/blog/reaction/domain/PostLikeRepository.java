package com.nhnacademy.blog.reaction.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByMemberIdAndPostId(Long memberId, Long postId);

    /**
     * 공감 넣기. 이미 있으면 UNIQUE(member_id, post_id) 때문에 아무것도 하지 않고 0을 돌려준다(MySQL INSERT IGNORE).
     * "있나 보고 넣기"를 두 번에 나눠 하면 동시에 온 두 요청이 둘 다 넣으려다 하나가 오류가 난다. 한 문장이라 그런 틈이 없다.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO post_like (post_id, member_id) VALUES (:postId, :memberId)", nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId, @Param("memberId") Long memberId);

    /** 공감 지우기. 지운 행 수(0 또는 1). */
    @Modifying
    @Query(value = "DELETE FROM post_like WHERE post_id = :postId AND member_id = :memberId", nativeQuery = true)
    int deleteIfPresent(@Param("postId") Long postId, @Param("memberId") Long memberId);

}
