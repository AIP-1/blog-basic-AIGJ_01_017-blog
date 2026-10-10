package com.nhnacademy.blog.member.presentation;

import com.nhnacademy.blog.global.auth.AuthCookieManager;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.web.RequestValidator;
import com.nhnacademy.blog.member.application.MeService;
import com.nhnacademy.blog.member.application.WithdrawalService;
import com.nhnacademy.blog.member.presentation.dto.MeResponse;
import com.nhnacademy.blog.member.presentation.dto.MeUpdateRequest;
import com.nhnacademy.blog.member.presentation.dto.PasswordChangeRequest;
import com.nhnacademy.blog.member.presentation.dto.WithdrawRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보와 회원정보 수정 (AUTH-04, T055 AUTH-05). 비회원이면 401이고, 프론트는 GET으로 로그인 상태를 확인한다.
 * 입력 검사는 로그인 확인(401) 뒤에 한다(RequestValidator, 상태 코드 순서 401 → 400).
 */
@RestController
public class MeController {

    private final MeService meService;
    private final WithdrawalService withdrawalService;
    private final AuthCookieManager cookieManager;
    private final RequestValidator requestValidator;

    public MeController(MeService meService, WithdrawalService withdrawalService, AuthCookieManager cookieManager,
                        RequestValidator requestValidator) {
        this.meService = meService;
        this.withdrawalService = withdrawalService;
        this.cookieManager = cookieManager;
        this.requestValidator = requestValidator;
    }

    @GetMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me(@AuthenticationPrincipal LoginMember member) {
        return MeResponse.from(meService.me(member.id()));
    }

    /** 닉네임·프로필 사진 바꾸기. 바뀐 내 정보를 돌려준다. */
    @PatchMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse update(@AuthenticationPrincipal LoginMember member, @RequestBody MeUpdateRequest request) {
        requestValidator.validate(request);
        meService.update(member.id(), request.nickname(), request.profileImageId());
        return MeResponse.from(meService.me(member.id()));
    }

    /** 비밀번호 바꾸기. 로그인 상태는 그대로 둔다. */
    @PutMapping("/api/me/password")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal LoginMember member,
                               @RequestBody PasswordChangeRequest request) {
        requestValidator.validate(request);
        meService.changePassword(member.id(), request.currentPassword(), request.newPassword());
    }

    /**
     * 회원 탈퇴 (AUTH-06). `{ password }`로 본인 확인 → 204와 로그인 쿠키 삭제. 비밀번호가 틀리면 401 LOGIN_FAILED,
     * 15분 안에 5번 틀리면 429(비밀번호 변경과 같은 횟수).
     */
    @DeleteMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal LoginMember member, @RequestBody WithdrawRequest request,
                         HttpServletResponse response) {
        withdrawalService.withdraw(member.id(), request.password());
        cookieManager.clear(response);
    }

}
