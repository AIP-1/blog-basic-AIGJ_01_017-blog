package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.application.AdminReportService;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.global.web.DateTimes;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 처리 대기 신고의 대상별 묶음 `{ targetType, targetId, targetPreview, target, reportCount, reasons, firstReportedAt }`.
 * targetPreview는 대상 이름(글 제목, 댓글 앞부분, 블로그 이름), target은 링크용 정보다.
 */
public record ReportGroupResponse(String targetType, Long targetId, String targetPreview, TargetResponse target,
                                  long reportCount, Map<String, Long> reasons, OffsetDateTime firstReportedAt) {

    public static ReportGroupResponse from(AdminReportService.PendingGroup group) {
        Map<String, Long> reasons = new LinkedHashMap<>();
        group.reasons().forEach((SanctionReason reason, Long count) -> reasons.put(reason.name(), count));
        TargetResponse target = TargetResponse.from(group.target());
        return new ReportGroupResponse(target.type(), target.id(), target.label(), target, group.reportCount(), reasons,
                DateTimes.toOffset(group.firstReportedAt()));
    }

}
