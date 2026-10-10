package com.nhnacademy.blog.admin.presentation;

import com.nhnacademy.blog.admin.application.AdminMemberService;
import com.nhnacademy.blog.admin.application.AdminReportService;
import com.nhnacademy.blog.admin.application.DashboardService;
import com.nhnacademy.blog.admin.application.ModerationLogService;
import com.nhnacademy.blog.admin.application.ModerationService;
import com.nhnacademy.blog.admin.application.Sanction;
import com.nhnacademy.blog.admin.application.SuspensionPeriod;
import com.nhnacademy.blog.admin.presentation.dto.AdminMemberDetailResponse;
import com.nhnacademy.blog.admin.presentation.dto.AdminMemberResponse;
import com.nhnacademy.blog.admin.presentation.dto.DashboardResponse;
import com.nhnacademy.blog.admin.presentation.dto.ModerationLogResponse;
import com.nhnacademy.blog.admin.presentation.dto.ReportGroupResponse;
import com.nhnacademy.blog.admin.presentation.dto.ResolveRequest;
import com.nhnacademy.blog.admin.presentation.dto.SanctionRequest;
import com.nhnacademy.blog.admin.presentation.dto.SuspensionRequest;
import com.nhnacademy.blog.admin.presentation.dto.TargetReportsResponse;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 관리 API (스텝 18: T094 회원, T095 숨김, T109 블로그 제한, T110 신고 처리, T112 대시보드·이력).
 * /api/admin/**은 SecurityConfig가 관리자(ROLE_ADMIN)만 통과시킨다. 비회원 401, 일반 회원 403.
 */
@RestController
public class AdminController {

    private final AdminMemberService adminMemberService;
    private final ModerationService moderationService;
    private final AdminReportService adminReportService;
    private final ModerationLogService moderationLogService;
    private final DashboardService dashboardService;

    public AdminController(AdminMemberService adminMemberService, ModerationService moderationService,
                           AdminReportService adminReportService, ModerationLogService moderationLogService,
                           DashboardService dashboardService) {
        this.adminMemberService = adminMemberService;
        this.moderationService = moderationService;
        this.adminReportService = adminReportService;
        this.moderationLogService = moderationLogService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/admin/dashboard")
    public DashboardResponse dashboard() {
        return DashboardResponse.from(dashboardService.dashboard());
    }

    // ---------- 회원 (ADMIN-02) ----------

    @GetMapping("/api/admin/members")
    public PageResponse<AdminMemberResponse> members(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Integer page) {
        return PageResponse.from(adminMemberService.search(q, status,
                PageQuery.of(page, null, AdminMemberService.PAGE_SIZE)), AdminMemberResponse::from);
    }

    @GetMapping("/api/admin/members/{id}")
    public AdminMemberDetailResponse member(@PathVariable Long id) {
        return AdminMemberDetailResponse.from(adminMemberService.detail(id));
    }

    @PostMapping("/api/admin/members/{id}/suspension")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id,
                        @RequestBody SuspensionRequest request) {
        moderationService.member(id);
        SuspensionPeriod period = SuspensionPeriod.of(request.period());
        moderationService.suspend(admin.id(), id, period, Sanction.of(request.reason(), request.reasonDetail()));
    }

    @DeleteMapping("/api/admin/members/{id}/suspension")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsuspend(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id) {
        moderationService.unsuspend(admin.id(), id);
    }

    // ---------- 글·댓글 숨김 (ADMIN-03), 블로그 이용 제한 (ADMIN-05) ----------

    @PostMapping("/api/admin/posts/{id}/blind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blindPost(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id,
                          @RequestBody SanctionRequest request) {
        moderationService.post(id);
        moderationService.blindPost(admin.id(), id, sanction(request));
    }

    @DeleteMapping("/api/admin/posts/{id}/blind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblindPost(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id) {
        moderationService.unblindPost(admin.id(), id);
    }

    @PostMapping("/api/admin/comments/{id}/blind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blindComment(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id,
                             @RequestBody SanctionRequest request) {
        moderationService.comment(id);
        moderationService.blindComment(admin.id(), id, sanction(request));
    }

    @DeleteMapping("/api/admin/comments/{id}/blind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblindComment(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id) {
        moderationService.unblindComment(admin.id(), id);
    }

    @PostMapping("/api/admin/blogs/{id}/restriction")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restrictBlog(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id,
                             @RequestBody SanctionRequest request) {
        moderationService.blog(id);
        moderationService.restrictBlog(admin.id(), id, sanction(request));
    }

    @DeleteMapping("/api/admin/blogs/{id}/restriction")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unrestrictBlog(@AuthenticationPrincipal LoginMember admin, @PathVariable Long id) {
        moderationService.unrestrictBlog(admin.id(), id);
    }

    // ---------- 신고 처리 (ADMIN-04) ----------

    @GetMapping("/api/admin/reports")
    public PageResponse<ReportGroupResponse> reports(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Integer page) {
        if (status != null && !status.isBlank() && !status.equals("PENDING")) {
            throw BusinessException.invalidField("status", "처리 대기(PENDING) 신고만 묶어서 봅니다.");
        }
        return PageResponse.from(adminReportService.pending(PageQuery.of(page, null, AdminReportService.PAGE_SIZE)),
                ReportGroupResponse::from);
    }

    @GetMapping("/api/admin/reports/{targetType}/{targetId}")
    public TargetReportsResponse targetReports(@PathVariable String targetType, @PathVariable Long targetId) {
        return TargetReportsResponse.from(adminReportService.reports(targetType, targetId));
    }

    @PostMapping("/api/admin/reports/{targetType}/{targetId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@AuthenticationPrincipal LoginMember admin, @PathVariable String targetType,
                        @PathVariable Long targetId, @RequestBody ResolveRequest request) {
        adminReportService.resolve(admin.id(), targetType, targetId, new AdminReportService.ResolveCommand(
                request.result(), request.reason(), request.reasonDetail(), request.period()));
    }

    // ---------- 관리 이력 (ADMIN-06) ----------

    @GetMapping("/api/admin/moderation-logs")
    public PageResponse<ModerationLogResponse> logs(@RequestParam(required = false) String targetType,
                                                    @RequestParam(required = false) Long targetId,
                                                    @RequestParam(required = false) Long adminId,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to,
                                                    @RequestParam(required = false) Integer page) {
        return PageResponse.from(moderationLogService.search(targetType, targetId, adminId, from, to,
                PageQuery.of(page, null, ModerationLogService.PAGE_SIZE)), ModerationLogResponse::from);
    }

    private static Sanction sanction(SanctionRequest request) {
        return Sanction.of(request.reason(), request.reasonDetail());
    }

}
