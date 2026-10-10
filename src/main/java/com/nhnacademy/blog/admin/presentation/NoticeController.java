package com.nhnacademy.blog.admin.presentation;

import com.nhnacademy.blog.admin.application.NoticeService;
import com.nhnacademy.blog.admin.presentation.dto.NoticeRequest;
import com.nhnacademy.blog.admin.presentation.dto.NoticeResponse;
import com.nhnacademy.blog.admin.presentation.dto.NoticeSummaryResponse;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 공지 (T111, ADMIN-06). 읽기는 누구나, 쓰기는 /api/admin/** 라 관리자만. */
@RestController
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/api/notices")
    public PageResponse<NoticeSummaryResponse> list(@RequestParam(required = false) Integer page) {
        return PageResponse.from(noticeService.list(PageQuery.of(page, null, NoticeService.PAGE_SIZE)),
                NoticeSummaryResponse::from);
    }

    /** 홈 상단 최신 공지 1개. 공지가 없으면 본문 없이 200이다(화면은 띠를 숨긴다). */
    @GetMapping("/api/notices/latest")
    public NoticeSummaryResponse latest() {
        return noticeService.latest().map(NoticeSummaryResponse::from).orElse(null);
    }

    @GetMapping("/api/notices/{id}")
    public NoticeResponse detail(@PathVariable Long id) {
        return NoticeResponse.from(noticeService.find(id));
    }

    @PostMapping("/api/admin/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeResponse write(@AuthenticationPrincipal LoginMember admin, @RequestBody NoticeRequest request) {
        return NoticeResponse.from(noticeService.write(admin.id(), request.title(), request.content()));
    }

    @PutMapping("/api/admin/notices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void edit(@PathVariable Long id, @RequestBody NoticeRequest request) {
        noticeService.edit(id, request.title(), request.content());
    }

    @DeleteMapping("/api/admin/notices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        noticeService.delete(id);
    }

}
