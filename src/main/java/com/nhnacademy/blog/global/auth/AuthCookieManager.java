package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.global.host.DomainProperties;
import com.nhnacademy.blog.member.domain.Member;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인 쿠키를 주고, 읽고, 지운다 (R-03).
 * 쿠키는 Domain=.{platform}; HttpOnly; SameSite=Lax라 모든 블로그 주소가 같은 로그인 상태를 본다.
 * Access 쿠키는 브라우저를 닫으면 사라진다. Refresh 쿠키는 로그인 유지를 고르면 만료 시각(14일)까지 남는다.
 * 고르지 않으면 브라우저를 닫을 때 사라지고, 그 전에도 무활동 시간(30분)이 지나면 서버에서 끝난다(AUTH-03).
 */
@Component
public class AuthCookieManager {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private final JwtTokenProvider tokenProvider;
    private final TokenStore tokenStore;
    private final AuthProperties authProperties;
    private final DomainProperties domainProperties;

    public AuthCookieManager(JwtTokenProvider tokenProvider, TokenStore tokenStore, AuthProperties authProperties,
                             DomainProperties domainProperties) {
        this.tokenProvider = tokenProvider;
        this.tokenStore = tokenStore;
        this.authProperties = authProperties;
        this.domainProperties = domainProperties;
    }

    /** 로그인·가입 성공 때 부른다. */
    public void login(HttpServletResponse response, Member member, boolean rememberMe) {
        IssuedToken refreshToken = tokenProvider.createRefreshToken(member.getId(), rememberMe);
        tokenStore.saveRefresh(refreshToken, member.getId(), rememberMe ? null : authProperties.idleTimeout());
        addCookie(response, REFRESH_COOKIE, refreshToken.value(),
                rememberMe ? authProperties.refreshTokenTtl() : null);
        issueAccess(response, new LoginMember(member.getId(), member.getRole()));
    }

    /** Access 토큰만 새로 준다. Refresh 토큰으로 로그인 상태를 이어 갈 때 쓴다. */
    public void issueAccess(HttpServletResponse response, LoginMember member) {
        IssuedToken accessToken = tokenProvider.createAccessToken(member.id(), member.role());
        addCookie(response, ACCESS_COOKIE, accessToken.value(), null);
    }

    /** 로그아웃: 토큰을 무효로 만들고 쿠키를 지운다. 모든 블로그 주소에서 로그아웃된다 (AUTH-02). */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        readCookie(request, ACCESS_COOKIE)
                .flatMap(token -> tokenProvider.parse(token, TokenType.ACCESS))
                .ifPresent(claims -> tokenStore.blockAccess(claims.id(), claims.expiresAt()));
        readCookie(request, REFRESH_COOKIE)
                .flatMap(token -> tokenProvider.parse(token, TokenType.REFRESH))
                .ifPresent(claims -> tokenStore.deleteRefresh(claims.id()));
        clear(response);
    }

    /** 쿠키만 지운다. 정지·탈퇴 회원이나 망가진 토큰일 때 쓴다. */
    public void clear(HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "", Duration.ZERO);
        addCookie(response, REFRESH_COOKIE, "", Duration.ZERO);
    }

    public Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    /** maxAge가 null이면 브라우저를 닫을 때 사라지는 쿠키다. */
    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(name, value)
                .domain(domainProperties.cookieDomain())
                .path("/")
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite("Lax");
        if (maxAge != null) {
            cookie.maxAge(maxAge);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

}
