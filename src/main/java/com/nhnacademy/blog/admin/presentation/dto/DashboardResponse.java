package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.application.DashboardService;
import java.util.List;

/** 관리자 대시보드 `{ todaySignups, todayPosts, pendingReports, recentModerations[5] }`. */
public record DashboardResponse(long todaySignups, long todayPosts, long pendingReports,
                                List<ModerationLogResponse> recentModerations) {

    public static DashboardResponse from(DashboardService.Dashboard dashboard) {
        return new DashboardResponse(dashboard.todaySignups(), dashboard.todayPosts(), dashboard.pendingReports(),
                dashboard.recentModerations().stream().map(ModerationLogResponse::from).toList());
    }

}
