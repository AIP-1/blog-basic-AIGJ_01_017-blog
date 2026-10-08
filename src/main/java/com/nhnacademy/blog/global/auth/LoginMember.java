package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.member.domain.Role;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 로그인한 회원. 컨트롤러에서 @AuthenticationPrincipal LoginMember로 받는다. 비회원이면 null이다.
 */
public record LoginMember(Long id, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

}
