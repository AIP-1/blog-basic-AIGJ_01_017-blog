package com.nhnacademy.blog.admin.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 신고 (ADMIN-04). 한 회원은 같은 대상을 한 번만 신고한다(UNIQUE reporter_id, target_type, target_id).
 * 관리자가 처리하면 그 대상의 대기 신고가 모두 DONE이 된다.
 */
@Entity
@Table(name = "report")
public class Report extends BaseCreatedEntity {

    public static final int DESCRIPTION_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private SanctionReason reason;

    @Column(name = "description", length = DESCRIPTION_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private ReportResult result;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected Report() {
    }

    public Long getId() {
        return id;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public SanctionReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public ReportResult getResult() {
        return result;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

}
