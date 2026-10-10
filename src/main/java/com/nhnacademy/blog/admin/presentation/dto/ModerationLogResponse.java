package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.application.ModerationLogService;
import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.global.web.DateTimes;
import java.time.OffsetDateTime;

/** 관리 이력 한 줄. 사유가 없는 조치(해제·기각)는 reason이 null이다. */
public record ModerationLogResponse(Long id, OffsetDateTime createdAt, Admin admin, String action, String reason,
                                    String reasonMessage, String reasonDetail, TargetResponse target) {

    public static ModerationLogResponse from(ModerationLogService.LogLine line) {
        ModerationLog log = line.log();
        return new ModerationLogResponse(log.getId(), DateTimes.toOffset(log.getCreatedAt()),
                new Admin(log.getAdmin().getId(), line.adminNickname()), log.getAction().name(),
                log.getReason() == null ? null : log.getReason().name(),
                log.getReason() == null ? null : log.getReason().getMessage(), log.getReasonDetail(),
                TargetResponse.from(line.target()));
    }

    public record Admin(Long id, String nickname) {
    }

}
