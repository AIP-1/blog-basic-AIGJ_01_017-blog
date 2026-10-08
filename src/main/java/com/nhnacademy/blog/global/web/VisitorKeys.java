package com.nhnacademy.blog.global.web;

import com.nhnacademy.blog.global.auth.AuthProperties;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.DomainProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 조회자·방문자를 구분하는 키 (view_log.viewer_key, 뒤에 blog_visit.visitor_key도 같은 방식).
 * 회원은 "m:{회원 id}"다. 비회원은 처음 볼 때 임의의 값(UUID)을 visitor_id 쿠키로 주고 "a:{그 값}"으로 쓴다.
 * 쿠키는 로그인 쿠키처럼 Domain=.{platform}이라 모든 블로그 주소에서 같은 비회원으로 보인다.
 * 쿠키를 지우거나 받지 않는 브라우저는 볼 때마다 새 비회원이 된다.
 */
@Component
public class VisitorKeys {

    public static final String COOKIE = "visitor_id";

    private static final Duration COOKIE_TTL = Duration.ofDays(365);

    private final DomainProperties domainProperties;
    private final AuthProperties authProperties;

    public VisitorKeys(DomainProperties domainProperties, AuthProperties authProperties) {
        this.domainProperties = domainProperties;
        this.authProperties = authProperties;
    }

    /** 지금 요청한 사람의 키. 쿠키가 없거나 망가진 비회원에게는 새 쿠키를 응답에 싣는다. */
    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        Long memberId = LoginMembers.currentId();
        if (memberId != null) {
            return "m:" + memberId;
        }
        String visitorId = readCookie(request).orElseGet(() -> issue(response));
        return "a:" + visitorId;
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .flatMap(value -> parseUuid(value).stream())
                .findFirst();
    }

    /** 쿠키 값은 사용자가 바꿀 수 있으므로 UUID 모양만 받는다(아무 글자나 viewer_key에 들어가지 않게). */
    private static Optional<String> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value).toString());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String issue(HttpServletResponse response) {
        String visitorId = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(COOKIE, visitorId)
                .domain(domainProperties.cookieDomain())
                .path("/")
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite("Lax")
                .maxAge(COOKIE_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return visitorId;
    }

}
