package com.nhnacademy.blog.member.application;

import com.nhnacademy.blog.blog.application.BlogDeletionService;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.auth.AttemptLimiter;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.member.domain.MemberTraceCleaner;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 (T108, AUTH-06). 본인 확인(이메일 가입 회원은 비밀번호) 뒤 한 트랜잭션에서:
 * 그 회원의 블로그를 모두 지우고(블로그 삭제와 같은 규칙, 대표 블로그 포함), 누른 공감·구독과 쓴 댓글·방명록을 정리하고,
 * 회원을 WITHDRAWN으로 바꿔 로그인 수단을 지운다. 이미 나간 로그인 쿠키는 인증 필터가 탈퇴 회원을 비회원으로 보며 지운다.
 * 소셜로만 가입한 회원의 재인증은 소셜 로그인(스텝 20)과 함께 붙이고, 그 전에는 본인 확인 실패(401)다.
 */
@Service
public class WithdrawalService {

    private final MemberRepository memberRepository;
    private final BlogRepository blogRepository;
    private final BlogDeletionService blogDeletionService;
    private final MemberTraceCleaner memberTraceCleaner;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter attemptLimiter;
    private final Clock clock;

    public WithdrawalService(MemberRepository memberRepository, BlogRepository blogRepository,
                             BlogDeletionService blogDeletionService, MemberTraceCleaner memberTraceCleaner,
                             PasswordEncoder passwordEncoder, AttemptLimiter attemptLimiter, Clock clock) {
        this.memberRepository = memberRepository;
        this.blogRepository = blogRepository;
        this.blogDeletionService = blogDeletionService;
        this.memberTraceCleaner = memberTraceCleaner;
        this.passwordEncoder = passwordEncoder;
        this.attemptLimiter = attemptLimiter;
        this.clock = clock;
    }

    @Transactional
    public void withdraw(Long memberId, String password) {
        Member member = memberRepository.findById(memberId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        confirm(member, password);
        LocalDateTime now = LocalDateTime.now(clock);
        for (Blog blog : blogRepository.findActiveByMemberId(memberId)) {
            blogDeletionService.deleteWithPosts(blog.getId());
        }
        memberTraceCleaner.clean(memberId, now);
        memberRepository.findById(memberId).orElseThrow().withdraw(now);
    }

    /**
     * 본인 확인. 비밀번호가 틀리면 401 LOGIN_FAILED. 비밀번호 변경과 같은 횟수 제한(15분 5번, R-17)을 같이 써서
     * 탈퇴 화면으로 비밀번호를 무작정 맞혀 보는 것도 막는다.
     */
    private void confirm(Member member, String password) {
        if (member.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        attemptLimiter.attempt("password-change:" + member.getId(), () -> {
            if (password == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
                throw new BusinessException(ErrorCode.LOGIN_FAILED);
            }
            return null;
        }, e -> true);
    }

}
