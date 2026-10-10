package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.Report;
import com.nhnacademy.blog.admin.domain.ReportRepository;
import com.nhnacademy.blog.admin.domain.ReportResult;
import com.nhnacademy.blog.admin.domain.ReportStatus;
import com.nhnacademy.blog.admin.domain.ReportTargetType;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 처리 (T110, ADMIN-04). 처리 대기 신고를 대상별로 묶어 보여 주고, 관리자가 결과 하나를 고르면
 * 그 대상의 대기 신고를 모두 처리 완료로 바꾼다. 조치와 이력은 ModerationService가 같은 트랜잭션에서 한다.
 */
@Service
public class AdminReportService {

    public static final int PAGE_SIZE = 20;

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final ModerationService moderationService;
    private final ModerationTargets moderationTargets;
    private final Clock clock;

    public AdminReportService(ReportRepository reportRepository, MemberRepository memberRepository,
                              ModerationService moderationService, ModerationTargets moderationTargets, Clock clock) {
        this.reportRepository = reportRepository;
        this.memberRepository = memberRepository;
        this.moderationService = moderationService;
        this.moderationTargets = moderationTargets;
        this.clock = clock;
    }

    /** 대상별 묶음, 신고 수 많은 순. 대상 이름과 사유별 수를 함께. */
    @Transactional(readOnly = true)
    public Page<PendingGroup> pending(PageQuery page) {
        Page<ReportRepository.PendingTarget> targets = reportRepository.findPendingTargets(
                page.toPageable(Sort.unsorted()));
        return targets.map(target -> {
            Map<SanctionReason, Long> reasons = new EnumMap<>(SanctionReason.class);
            reportRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAscIdAsc(
                            target.getTargetType(), target.getTargetId(), ReportStatus.PENDING)
                    .forEach(report -> reasons.merge(report.getReason(), 1L, Long::sum));
            return new PendingGroup(moderationTargets.of(target.getTargetType().toModerationTarget(),
                    target.getTargetId()), target.getReportCount(), reasons, target.getFirstReportedAt());
        });
    }

    /** 그 대상의 신고 목록(처리한 것 포함), 먼저 신고한 순. 신고한 회원의 닉네임을 함께. */
    @Transactional(readOnly = true)
    public TargetReports reports(String rawType, Long targetId) {
        ReportTargetType type = parseType(rawType);
        List<Report> reports = reportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(type, targetId);
        Map<Long, Member> reporters = memberRepository.findAllById(
                        reports.stream().map(Report::getReporterId).distinct().toList()).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        return new TargetReports(moderationTargets.of(type.toModerationTarget(), targetId),
                reports.stream().map(report -> new ReportLine(report, reporters.get(report.getReporterId()))).toList());
    }

    /**
     * 처리. 대기 신고가 없는 대상 404. 결과마다:
     * BLIND 글·댓글 숨김(블로그는 400), RESTRICT_BLOG 블로그 또는 글이 속한 블로그(댓글은 400),
     * SUSPEND 글·블로그 주인이나 댓글 작성자를 정지(period 필수), REJECT 기각(이력만).
     */
    @Transactional
    public void resolve(Long adminId, String rawType, Long targetId, ResolveCommand command) {
        ReportTargetType type = parseType(rawType);
        if (reportRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAscIdAsc(type, targetId,
                ReportStatus.PENDING).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ReportResult result = parseResult(command.result());
        switch (result) {
            case BLIND -> {
                Sanction sanction = Sanction.of(command.reason(), command.reasonDetail());
                switch (type) {
                    case POST -> moderationService.blindPost(adminId, targetId, sanction);
                    case COMMENT -> moderationService.blindComment(adminId, targetId, sanction);
                    case BLOG -> throw BusinessException.invalidField("result", "블로그는 이용 제한을 골라 주세요.");
                }
            }
            case RESTRICT_BLOG -> {
                Sanction sanction = Sanction.of(command.reason(), command.reasonDetail());
                Long blogId = switch (type) {
                    case BLOG -> targetId;
                    case POST -> moderationService.post(targetId).getBlog().getId();
                    case COMMENT -> throw BusinessException.invalidField("result",
                            "댓글 신고는 블라인드나 작성자 정지를 골라 주세요.");
                };
                moderationService.restrictBlog(adminId, blogId, sanction);
            }
            case SUSPEND -> {
                SuspensionPeriod period = SuspensionPeriod.of(command.period());
                Sanction sanction = Sanction.of(command.reason(), command.reasonDetail());
                Long memberId = switch (type) {
                    case POST -> moderationService.post(targetId).getBlog().getMember().getId();
                    case COMMENT -> moderationService.comment(targetId).getMember().getId();
                    case BLOG -> moderationService.blog(targetId).getMember().getId();
                };
                moderationService.suspend(adminId, memberId, period, sanction);
            }
            case REJECT -> moderationService.rejectReport(adminId, type.toModerationTarget(), targetId);
        }
        reportRepository.resolveAll(type, targetId, result, LocalDateTime.now(clock));
    }

    private static ReportTargetType parseType(String value) {
        return Arrays.stream(ReportTargetType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static ReportResult parseResult(String value) {
        return Arrays.stream(ReportResult.values())
                .filter(result -> result.name().equals(value))
                .findFirst()
                .orElseThrow(() -> BusinessException.invalidField("result",
                        "BLIND, RESTRICT_BLOG, SUSPEND, REJECT 중 하나입니다."));
    }

    public record PendingGroup(ModerationTargets.TargetView target, long reportCount, Map<SanctionReason, Long> reasons,
                               LocalDateTime firstReportedAt) {
    }

    public record TargetReports(ModerationTargets.TargetView target, List<ReportLine> reports) {
    }

    public record ReportLine(Report report, Member reporter) {
    }

    public record ResolveCommand(String result, String reason, String reasonDetail, String period) {
    }

}
