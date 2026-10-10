package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.application.AdminReportService;
import com.nhnacademy.blog.admin.domain.Report;
import com.nhnacademy.blog.global.web.DateTimes;
import java.time.OffsetDateTime;
import java.util.List;

/** 한 대상의 신고 목록(처리한 것 포함, 먼저 신고한 순)과 대상 정보. */
public record TargetReportsResponse(TargetResponse target, List<Line> reports) {

    public static TargetReportsResponse from(AdminReportService.TargetReports reports) {
        return new TargetReportsResponse(TargetResponse.from(reports.target()),
                reports.reports().stream().map(Line::from).toList());
    }

    public record Line(Long id, Reporter reporter, String reason, String reasonMessage, String description,
                       String status, String result, OffsetDateTime createdAt, OffsetDateTime processedAt) {

        static Line from(AdminReportService.ReportLine line) {
            Report report = line.report();
            return new Line(report.getId(),
                    line.reporter() == null ? null : new Reporter(line.reporter().getId(), line.reporter().getNickname()),
                    report.getReason().name(), report.getReason().getMessage(), report.getDescription(),
                    report.getStatus().name(), report.getResult() == null ? null : report.getResult().name(),
                    DateTimes.toOffset(report.getCreatedAt()), DateTimes.toOffset(report.getProcessedAt()));
        }

    }

    public record Reporter(Long id, String nickname) {
    }

}
