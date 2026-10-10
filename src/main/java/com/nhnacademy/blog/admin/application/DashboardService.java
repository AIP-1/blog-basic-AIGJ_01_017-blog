package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.ReportRepository;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드 (T112, ADMIN-06). 오늘(한국 시간 0시부터) 가입한 회원 수, 오늘 발행된 글 수(지운 글 빼고),
 * 처리 대기 신고 수(신고 건수가 아니라 처리할 대상 수), 최근 관리 이력 5개.
 */
@Service
public class DashboardService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final ModerationLogService moderationLogService;
    private final Clock clock;

    public DashboardService(MemberRepository memberRepository, PostRepository postRepository,
                            ReportRepository reportRepository, ModerationLogService moderationLogService, Clock clock) {
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
        this.reportRepository = reportRepository;
        this.moderationLogService = moderationLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        LocalDateTime today = LocalDate.now(clock).atStartOfDay();
        return new Dashboard(memberRepository.countByCreatedAtGreaterThanEqual(today),
                postRepository.countPublishedSince(today), reportRepository.countPendingTargets(),
                moderationLogService.recent());
    }

    public record Dashboard(long todaySignups, long todayPosts, long pendingReports,
                            List<ModerationLogService.LogLine> recentModerations) {
    }

}
