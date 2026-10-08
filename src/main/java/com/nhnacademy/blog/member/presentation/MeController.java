package com.nhnacademy.blog.member.presentation;

import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.member.application.MeService;
import com.nhnacademy.blog.member.presentation.dto.MeResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보. 비회원이면 401이고, 프론트는 이것으로 로그인 상태를 확인한다 (AUTH-04).
 */
@RestController
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me(@AuthenticationPrincipal LoginMember member) {
        return MeResponse.from(meService.me(member.id()));
    }

}
