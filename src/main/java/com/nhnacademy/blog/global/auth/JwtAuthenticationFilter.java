package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.ErrorResponseWriter;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 쿠키로 회원을 확인한다 (T007, T008, R-03).
 * <ol>
 *   <li>Access 토큰이 유효하면 그 회원. 만료됐으면 살아 있는 Refresh 토큰으로 Access 토큰을 다시 준다.</li>
 *   <li>요청마다 회원 상태를 DB에서 확인한다. 정지 회원은 API 요청부터 403 MEMBER_SUSPENDED와 쿠키 삭제.</li>
 *   <li>탈퇴했거나 없는 회원, 망가진 토큰이면 쿠키를 지우고 비회원으로 처리한다.
 *       로그인이 필요한 행동이면 뒤에서 401이 된다.</li>
 * </ol>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthCookieManager cookieManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenStore tokenStore;
    private final MemberRepository memberRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final ErrorResponseWriter errorResponseWriter;
    private final Clock clock;

    public JwtAuthenticationFilter(AuthCookieManager cookieManager, JwtTokenProvider tokenProvider,
                                   TokenStore tokenStore, MemberRepository memberRepository,
                                   ModerationLogRepository moderationLogRepository,
                                   ErrorResponseWriter errorResponseWriter, Clock clock) {
        this.cookieManager = cookieManager;
        this.tokenProvider = tokenProvider;
        this.tokenStore = tokenStore;
        this.memberRepository = memberRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.errorResponseWriter = errorResponseWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> accessCookie = cookieManager.readCookie(request, AuthCookieManager.ACCESS_COOKIE);
        Optional<String> refreshCookie = cookieManager.readCookie(request, AuthCookieManager.REFRESH_COOKIE);
        if (accessCookie.isEmpty() && refreshCookie.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<Long> accessMemberId = accessCookie
                .flatMap(token -> tokenProvider.parse(token, TokenType.ACCESS))
                .filter(claims -> !tokenStore.isAccessBlocked(claims.id()))
                .map(TokenClaims::memberId);
        Optional<Long> memberId = accessMemberId.or(() -> refreshCookie
                .flatMap(token -> tokenProvider.parse(token, TokenType.REFRESH))
                .filter(claims -> tokenStore.isRefreshActive(claims.id()))
                .map(TokenClaims::memberId));

        Optional<Member> member = memberId.flatMap(memberRepository::findById);
        if (member.isEmpty() || member.get().isWithdrawn()) {
            cookieManager.clear(response);
            chain.doFilter(request, response);
            return;
        }

        if (member.get().isSuspendedAt(LocalDateTime.now(clock))) {
            if (isApiRequest(request)) {
                cookieManager.clear(response);
                errorResponseWriter.write(response,
                        new BusinessException(ErrorCode.MEMBER_SUSPENDED, suspensionDetail(member.get())));
                return;
            }
            // 화면 요청은 비회원으로 그린다. 화면이 부르는 첫 API에서 정지 안내를 받는다.
            chain.doFilter(request, response);
            return;
        }

        LoginMember loginMember = new LoginMember(member.get().getId(), member.get().getRole());
        if (accessMemberId.isEmpty()) {
            cookieManager.issueAccess(response, loginMember);
        }
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(loginMember, null, loginMember.authorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private SuspensionDetail suspensionDetail(Member member) {
        SanctionReason reason = moderationLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
                        ModerationTargetType.MEMBER, member.getId(), ModerationAction.SUSPEND)
                .map(ModerationLog::getReason)
                .orElse(null);
        OffsetDateTime until = member.getSuspendedUntil() == null
                ? null
                : member.getSuspendedUntil().atZone(clock.getZone()).toOffsetDateTime();
        return new SuspensionDetail(reason == null ? null : reason.name(),
                reason == null ? null : reason.getMessage(), until);
    }

}
