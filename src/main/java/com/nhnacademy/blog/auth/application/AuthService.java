package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.Emails;
import com.nhnacademy.blog.auth.domain.PasswordRule;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 가입 (T018, AUTH-01, OWN-01).
 */
@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(MemberRepository memberRepository, EmailVerificationService emailVerificationService,
                       PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 가입. 이메일·닉네임 중복과 비밀번호 규칙을 보고, 인증 코드를 확인한 뒤에만 회원을 만든다.
     * 코드 확인(verified_at 기록)과 회원 생성이 한 트랜잭션이라 하나가 실패하면 둘 다 되돌아간다.
     */
    @Transactional
    public Member signup(String rawEmail, String code, String password, String rawNickname) {
        String email = Emails.normalize(rawEmail);
        String nickname = rawNickname.trim();
        PasswordRule.check("password", password);
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }
        emailVerificationService.consume(email, code);
        try {
            return memberRepository.saveAndFlush(Member.ofEmail(email, passwordEncoder.encode(password), nickname));
        } catch (DataIntegrityViolationException e) {
            // 중복 확인과 저장 사이에 같은 값으로 먼저 가입한 사람이 있다
            throw new BusinessException(isNicknameConflict(e) ? ErrorCode.NICKNAME_TAKEN : ErrorCode.EMAIL_TAKEN);
        }
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !memberRepository.existsByNickname(nickname.trim());
    }

    private boolean isNicknameConflict(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_member_nickname");
    }

}
