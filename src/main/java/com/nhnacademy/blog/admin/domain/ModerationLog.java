package com.nhnacademy.blog.admin.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import com.nhnacademy.blog.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 관리 이력. INSERT만 한다. 제재 사유는 대상의 최신 제재 행에서 읽는다.
 */
@Entity
@Table(name = "moderation_log")
public class ModerationLog extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Member admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ModerationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 20)
    private SanctionReason reason;

    @Column(name = "reason_detail", length = 200)
    private String reasonDetail;

    protected ModerationLog() {
    }

    private ModerationLog(Member admin, ModerationAction action, ModerationTargetType targetType, Long targetId,
                          SanctionReason reason, String reasonDetail) {
        this.admin = admin;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.reasonDetail = reasonDetail;
    }

    public static ModerationLog record(Member admin, ModerationAction action, ModerationTargetType targetType,
                                       Long targetId, SanctionReason reason, String reasonDetail) {
        return new ModerationLog(admin, action, targetType, targetId, reason, reasonDetail);
    }

    public Long getId() {
        return id;
    }

    public Member getAdmin() {
        return admin;
    }

    public ModerationAction getAction() {
        return action;
    }

    public ModerationTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public SanctionReason getReason() {
        return reason;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

}
