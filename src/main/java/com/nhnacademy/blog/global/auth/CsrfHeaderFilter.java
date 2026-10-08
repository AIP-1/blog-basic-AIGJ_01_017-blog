package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 쿠키 인증의 CSRF 대책 (R-03). 상태를 바꾸는 API 요청은 X-Requested-With: XMLHttpRequest가 있어야 한다.
 * 다른 사이트의 폼이나 단순 요청은 이 헤더를 붙일 수 없다. SameSite=Lax 쿠키와 함께 쓴다.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    public static final String EXPECTED_VALUE = "XMLHttpRequest";

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ErrorResponseWriter errorResponseWriter;

    public CsrfHeaderFilter(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean stateChanging = STATE_CHANGING_METHODS.contains(request.getMethod());
        boolean api = request.getRequestURI().startsWith("/api/");
        if (stateChanging && api && !EXPECTED_VALUE.equals(request.getHeader(HEADER))) {
            errorResponseWriter.write(response, ErrorCode.CSRF_REJECTED);
            return;
        }
        chain.doFilter(request, response);
    }

}
