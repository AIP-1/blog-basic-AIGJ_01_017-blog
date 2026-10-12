package com.nhnacademy.blog.bookmark.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, Long> {

    /**
     * 저장. 이미 저장했으면(UNIQUE) 아무것도 하지 않고 0. 연달아 눌러도 행은 하나다(공감과 같은 방법).
     * 다시 저장해도 처음 저장할 때의 시각·제목이 남는다.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO post_bookmark (post_id, member_id, title_snapshot, blog_name_snapshot)
            VALUES (:postId, :memberId, :title, :blogName)""", nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId, @Param("memberId") Long memberId, @Param("title") String title,
                       @Param("blogName") String blogName);

    @Modifying
    @Query(value = "DELETE FROM post_bookmark WHERE member_id = :memberId AND post_id = :postId", nativeQuery = true)
    int deleteIfPresent(@Param("memberId") Long memberId, @Param("postId") Long postId);

    boolean existsByMemberIdAndPostId(Long memberId, Long postId);

    /** 저장 최신순 첫 묶음(같은 시각이면 나중 행이 위). 인덱스 idx_post_bookmark_member_id_created_at_id. */
    @Query("select b from PostBookmark b where b.memberId = :memberId order by b.createdAt desc, b.id desc")
    List<PostBookmark> findLatest(@Param("memberId") Long memberId, Limit limit);

    /** 커서(마지막으로 받은 행의 저장 시각·번호) 뒤 묶음. */
    @Query("""
            select b from PostBookmark b where b.memberId = :memberId
              and (b.createdAt < :time or (b.createdAt = :time and b.id < :id))
            order by b.createdAt desc, b.id desc""")
    List<PostBookmark> findLatestAfter(@Param("memberId") Long memberId, @Param("time") LocalDateTime time,
                                       @Param("id") Long id, Limit limit);

}
