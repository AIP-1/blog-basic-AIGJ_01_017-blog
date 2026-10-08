package com.nhnacademy.blog.global.auth;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 컨트롤러 밖(필터, 인자 해석기)에서 지금 로그인한 회원을 꺼낸다.
 */
public final class LoginMembers {

    private LoginMembers() {
    }

    public static Optional<LoginMember> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginMember member) {
            return Optional.of(member);
        }
        return Optional.empty();
    }

    public static Long currentId() {
        return current().map(LoginMember::id).orElse(null);
    }

}
