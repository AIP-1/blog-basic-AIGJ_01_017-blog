package com.nhnacademy.blog.auth.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * 로그인 끝내기 (인증의 입구, 스텝 19b). 로그아웃과 같다: 지금 Access 토큰을 막고, Refresh 토큰을 지우고, 쿠키를 지운다.
 * 회원 기능(탈퇴 AUTH-06)은 쿠키 이름이나 토큰 저장소를 몰라도 이것만 부르면 된다.
 */
@Component
public class LoginSessions {

    private final AuthCookieManager cookieManager;

    public LoginSessions(AuthCookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }

    public void end(HttpServletRequest request, HttpServletResponse response) {
        cookieManager.logout(request, response);
    }

}
