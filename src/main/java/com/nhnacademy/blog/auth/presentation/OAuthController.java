package com.nhnacademy.blog.auth.presentation;

import com.nhnacademy.blog.auth.application.OAuthMode;
import com.nhnacademy.blog.auth.application.SocialAuthService;
import com.nhnacademy.blog.auth.domain.SocialProvider;
import com.nhnacademy.blog.auth.presentation.dto.SocialSignupRequest;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.host.BlogAddressResolver;
import com.nhnacademy.blog.member.application.MeService;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.presentation.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 소셜 로그인·가입·연동·해제·재인증 (T099, T100, AUTH-01 소셜, OWN-03, AUTH-06). 흐름은 SocialAuthService에 적었다.
 * 브라우저가 주소를 직접 여는 GET(authorize, callback, complete)은 화면 이동이라 302로 답한다.
 */
@RestController
public class OAuthController {

    private final SocialAuthService socialAuthService;
    private final AuthCookieManager cookieManager;
    private final MeService meService;
    private final BlogAddressResolver hostResolver;

    public OAuthController(SocialAuthService socialAuthService, AuthCookieManager cookieManager, MeService meService,
                           BlogAddressResolver hostResolver) {
        this.socialAuthService = socialAuthService;
        this.cookieManager = cookieManager;
        this.meService = meService;
        this.hostResolver = hostResolver;
    }

    /**
     * 제공사 로그인 화면으로 302. mode: login(기본)·link(마이페이지 연동)·reauth(탈퇴 본인 확인), redirect: 로그인 뒤 돌아갈 곳.
     * link·reauth를 비회원이 열면 로그인 화면으로, 앱 키가 없는 제공사면 시작한 화면에 OAUTH_UNAVAILABLE로 돌려보낸다.
     */
    @GetMapping("/api/auth/oauth/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider, @RequestParam(required = false) String mode,
                                          @RequestParam(required = false) String redirect,
                                          HttpServletRequest request, HttpServletResponse response) {
        SocialProvider socialProvider = provider(provider);
        OAuthMode oauthMode = OAuthMode.fromParam(mode);
        Long memberId = LoginMembers.currentId();
        String origin = hostResolver.platformUrl(request, "");
        if (oauthMode != OAuthMode.LOGIN && memberId == null) {
            String back = oauthMode == OAuthMode.LINK ? "/me" : "/me/withdraw";
            return found(origin + "/login?redirect=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
        }
        return socialAuthService.start(socialProvider, oauthMode, redirect, memberId, origin)
                .map(start -> {
                    cookieManager.issueOAuthState(response, start.state(), SocialAuthService.STATE_TTL);
                    return found(start.providerUrl());
                })
                .orElseGet(() -> found(origin + SocialAuthService.failure(oauthMode, "OAUTH_UNAVAILABLE")));
    }

    /** 제공사가 돌려보내는 곳(콘솔에 등록한 리다이렉트 URI). 시작한 플랫폼 주소의 complete로 302. */
    @GetMapping("/api/auth/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error, HttpServletRequest request) {
        return found(socialAuthService.callback(provider(provider), code, state, error)
                .orElseGet(() -> hostResolver.platformUrl(request, "/login?error=OAUTH_FAILED")));
    }

    /**
     * 플랫폼 주소에서 끝내기. state 쿠키를 지우고, 로그인이면 로그인 쿠키(로그인 유지는 고르지 않은 상태)를 준 뒤 갈 곳으로 302.
     * 갈 곳: 로그인 → 돌아갈 곳, 처음 보는 계정 → /signup/social, 연동 → /me?linked= 또는 /me?error=, 재인증 → /me/withdraw?reauth=ok.
     */
    @GetMapping("/api/auth/oauth/complete")
    public ResponseEntity<Void> complete(@RequestParam(required = false) String ticket, HttpServletRequest request,
                                         HttpServletResponse response) {
        String stateCookie = cookieManager.readCookie(request, AuthCookieManager.OAUTH_STATE_COOKIE).orElse(null);
        cookieManager.clearOAuthState(response);
        SocialAuthService.Completion completion = socialAuthService.complete(ticket, stateCookie,
                LoginMembers.currentId());
        if (completion.login() != null) {
            cookieManager.login(response, completion.login(), false);
        }
        return found(completion.location());
    }

    /** 처음 보는 소셜 계정의 닉네임 확인 뒤 가입. 201 Me + 로그인 쿠키. */
    @PostMapping("/api/auth/oauth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signup(@Valid @RequestBody SocialSignupRequest request, HttpServletResponse response) {
        Member member = socialAuthService.signup(request.signupToken(), request.nickname());
        cookieManager.login(response, member, false);
        return MeResponse.from(meService.me(member.getId()));
    }

    /** 연동 해제 (OWN-03). 204. 남는 로그인 수단이 없으면 409 LAST_LOGIN_METHOD. 연동은 authorize?mode=link. */
    @DeleteMapping("/api/me/social/{provider}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@AuthenticationPrincipal LoginMember member, @PathVariable String provider) {
        socialAuthService.unlink(member.id(), provider(provider));
    }

    private static SocialProvider provider(String value) {
        return SocialProvider.fromPath(value).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static ResponseEntity<Void> found(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

}
