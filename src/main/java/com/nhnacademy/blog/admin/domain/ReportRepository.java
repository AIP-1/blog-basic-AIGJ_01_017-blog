package com.nhnacademy.blog.admin.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 신고를 넣는다. 같은 회원이 같은 대상을 이미 신고했으면(UNIQUE) 아무것도 하지 않고 0을 돌려준다.
     * 확인 → INSERT로 하면 동시에 두 번 누를 때 늦은 쪽이 UNIQUE 위반 500이다(태그·공감과 같은 방법).
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO report (reporter_id, target_type, target_id, reason, description, status)
            VALUES (:reporterId, :targetType, :targetId, :reason, :description, 'PENDING')""", nativeQuery = true)
    int insertIfAbsent(@Param("reporterId") Long reporterId, @Param("targetType") String targetType,
                       @Param("targetId") Long targetId, @Param("reason") String reason,
                       @Param("description") String description);

    /** 처리 대기 신고를 대상별로 묶어 신고 수 많은 순, 같으면 먼저 신고된 순 (ADMIN-04). */
    @Query(value = """
            select r.targetType as targetType, r.targetId as targetId, count(r) as reportCount,
                   min(r.createdAt) as firstReportedAt
            from Report r where r.status = com.nhnacademy.blog.admin.domain.ReportStatus.PENDING
            group by r.targetType, r.targetId
            order by count(r) desc, min(r.createdAt) asc""",
            countQuery = """
            select count(distinct concat(r.targetType, ':', r.targetId)) from Report r
            where r.status = com.nhnacademy.blog.admin.domain.ReportStatus.PENDING""")
    Page<PendingTarget> findPendingTargets(Pageable pageable);

    /** 처리 대기 중인 대상 수 (대시보드). */
    @Query("""
            select count(distinct concat(r.targetType, ':', r.targetId)) from Report r
            where r.status = com.nhnacademy.blog.admin.domain.ReportStatus.PENDING""")
    long countPendingTargets();

    List<Report> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAscIdAsc(ReportTargetType targetType,
                                                                            Long targetId, ReportStatus status);

    List<Report> findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(ReportTargetType targetType, Long targetId);

    /** 그 대상의 대기 신고를 모두 처리 완료로. 바뀐 행 수. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Report r set r.status = com.nhnacademy.blog.admin.domain.ReportStatus.DONE,
                   r.result = :result, r.processedAt = :now
            where r.targetType = :targetType and r.targetId = :targetId
              and r.status = com.nhnacademy.blog.admin.domain.ReportStatus.PENDING""")
    int resolveAll(@Param("targetType") ReportTargetType targetType, @Param("targetId") Long targetId,
                   @Param("result") ReportResult result, @Param("now") LocalDateTime now);

    /**
     * 회원이 받은 신고 수 (ADMIN-02 회원 상세). 그 회원의 글·댓글·블로그를 대상으로 한 신고를 모두 센다(처리한 것 포함).
     */
    @Query(value = """
            SELECT COUNT(*) FROM report r
            WHERE (r.target_type = 'POST' AND r.target_id IN
                    (SELECT p.id FROM post p JOIN blog b ON b.id = p.blog_id WHERE b.member_id = :memberId))
               OR (r.target_type = 'COMMENT' AND r.target_id IN (SELECT c.id FROM comment c WHERE c.member_id = :memberId))
               OR (r.target_type = 'BLOG' AND r.target_id IN (SELECT b.id FROM blog b WHERE b.member_id = :memberId))""",
            nativeQuery = true)
    long countReceivedByMember(@Param("memberId") Long memberId);

    /** 대상별 묶음 한 줄. */
    interface PendingTarget {
        ReportTargetType getTargetType();

        Long getTargetId();

        Long getReportCount();

        LocalDateTime getFirstReportedAt();
    }

}
