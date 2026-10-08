package com.nhnacademy.blog.auth.presentation;

import com.nhnacademy.blog.auth.application.AuthService;
import com.nhnacademy.blog.auth.presentation.dto.LoginRequest;
import com.nhnacademy.blog.auth.presentation.dto.NicknameAvailabilityResponse;
import com.nhnacademy.blog.auth.presentation.dto.SignupRequest;
import com.nhnacademy.blog.global.auth.AuthCookieManager;
import com.nhnacademy.blog.member.application.MeService;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.presentation.dto.MeResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입·로그인·로그아웃 (AUTH-01, AUTH-02). 성공하면 로그인 쿠키를 함께 준다.
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final MeService meService;
    private final AuthCookieManager cookieManager;

    public AuthController(AuthService authService, MeService meService, AuthCookieManager cookieManager) {
        this.authService = authService;
        this.meService = meService;
        this.cookieManager = cookieManager;
    }

    /** 가입하면 바로 로그인된다(로그인 유지는 고르지 않은 상태). */
    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        Member member = authService.signup(request.email(), request.code(), request.password(), request.nickname());
        cookieManager.login(response, member, false);
        return MeResponse.from(meService.me(member.getId()));
    }

    /** 로그인 유지(rememberMe)를 고르면 14일, 아니면 브라우저를 닫거나 30분 동안 요청이 없으면 끝난다 (AUTH-03). */
    @PostMapping("/api/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        Member member = authService.login(request.email(), request.password());
        cookieManager.login(response, member, request.rememberMe());
        return MeResponse.from(meService.me(member.getId()));
    }

    @GetMapping("/api/auth/nickname-availability")
    public NicknameAvailabilityResponse nicknameAvailability(
            @RequestParam @NotBlank(message = "닉네임을 입력해 주세요.")
            @Size(max = 20, message = "닉네임은 20자까지입니다.") String nickname) {
        return new NicknameAvailabilityResponse(authService.isNicknameAvailable(nickname));
    }

}
