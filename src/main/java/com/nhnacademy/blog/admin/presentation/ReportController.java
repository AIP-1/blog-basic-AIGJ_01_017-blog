package com.nhnacademy.blog.admin.presentation;

import com.nhnacademy.blog.admin.application.ReportService;
import com.nhnacademy.blog.admin.presentation.dto.ReportRequest;
import com.nhnacademy.blog.global.auth.LoginMember;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 신고 (T110, ADMIN-04). 어느 주소에서 불러도 같다(대상은 본문의 종류·번호로). */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@AuthenticationPrincipal LoginMember member, @RequestBody ReportRequest request) {
        reportService.report(member, request.targetType(), request.targetId(), request.reason(),
                request.description());
    }

}
