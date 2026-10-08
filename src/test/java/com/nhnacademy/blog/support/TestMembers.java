package com.nhnacademy.blog.support;

import com.nhnacademy.blog.global.auth.AuthCookieManager;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * 테스트용 회원과 로그인 쿠키를 만든다. 테스트끼리 DB를 함께 쓰므로 이메일·닉네임을 매번 다르게 한다.
 */
@Component
public class TestMembers {

    private final MemberRepository memberRepository;
    private final AuthCookieManager cookieManager;

    public TestMembers(MemberRepository memberRepository, AuthCookieManager cookieManager) {
        this.memberRepository = memberRepository;
        this.cookieManager = cookieManager;
    }

    public Member create() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(Member.ofEmail(suffix + "@blog.test", "hash", "m-" + suffix));
    }

    public Cookie[] loginCookies(Member member) {
        return loginCookies(member, false);
    }

    public Cookie[] loginCookies(Member member, boolean rememberMe) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieManager.login(response, member, rememberMe);
        return response.getCookies();
    }

    public Member admin() {
        return memberRepository.findAll().stream()
                .filter(member -> "admin@blog.test".equals(member.getEmail()))
                .findFirst()
                .orElseThrow();
    }

}
