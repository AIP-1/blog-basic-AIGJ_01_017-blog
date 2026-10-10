package com.nhnacademy.blog.admin.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long>,
        JpaSpecificationExecutor<ModerationLog> {

    Optional<ModerationLog> findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
            ModerationTargetType targetType, Long targetId, ModerationAction action);

    /** 대시보드의 최근 제재 5개(해제·기각 포함 모든 조치). 처리한 관리자를 함께 읽는다. */
    @Query("select l from ModerationLog l join fetch l.admin order by l.createdAt desc, l.id desc limit 5")
    List<ModerationLog> findRecent();

    /**
     * 회원 상세의 제재 이력 (ADMIN-02). 회원 자신과, 그 회원의 블로그·글·댓글에 대한 조치를 최신순으로.
     */
    @Query(value = """
            SELECT l.* FROM moderation_log l
            WHERE (l.target_type = 'MEMBER' AND l.target_id = :memberId)
               OR (l.target_type = 'BLOG' AND l.target_id IN (SELECT b.id FROM blog b WHERE b.member_id = :memberId))
               OR (l.target_type = 'POST' AND l.target_id IN
                    (SELECT p.id FROM post p JOIN blog b ON b.id = p.blog_id WHERE b.member_id = :memberId))
               OR (l.target_type = 'COMMENT' AND l.target_id IN (SELECT c.id FROM comment c WHERE c.member_id = :memberId))
            ORDER BY l.created_at DESC, l.id DESC
            LIMIT 50""", nativeQuery = true)
    List<ModerationLog> findAboutMember(@Param("memberId") Long memberId);

}
