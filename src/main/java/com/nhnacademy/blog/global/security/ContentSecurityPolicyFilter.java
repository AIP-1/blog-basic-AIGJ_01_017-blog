package com.nhnacademy.blog.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 응답에 CSP 헤더를 붙여 인라인 스크립트와 외부 스크립트를 막는다 (R-05).
 * 에디터·React가 넣는 인라인 style은 허용한다. 본문 HTML의 style 속성은 HtmlSanitizer가 먼저 지운다.
 */
@Component
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    static final String POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", POLICY);
        filterChain.doFilter(request, response);
    }

}
