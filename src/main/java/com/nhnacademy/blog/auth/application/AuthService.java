package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.Emails;
import com.nhnacademy.blog.auth.domain.PasswordRule;
import com.nhnacademy.blog.global.auth.SuspensionDetails;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 가입과 로그인 (T018, T019, AUTH-01, OWN-01, ADMIN-02).
 */
@Service
public class AuthService {

    private final DeviceTokens deviceTokens;

    private final MemberRepository memberRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final SuspensionDetails suspensionDetails;
    private final Clock clock;
    private final AttemptLimiter attemptLimiter;
    /** 없는 이메일로 로그인할 때도 비밀번호를 비교해, 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private final String dummyPasswordHash;

    public AuthService(MemberRepository memberRepository, EmailVerificationService emailVerificationService,
                       PasswordEncoder passwordEncoder, SuspensionDetails suspensionDetails, Clock clock,
                       AttemptLimiter attemptLimiter,
                       DeviceTokens deviceTokens) {
        this.deviceTokens = deviceTokens;
        this.memberRepository = memberRepository;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.suspensionDetails = suspensionDetails;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-1");
        this.attemptLimiter = attemptLimiter;
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

    /**
     * 이메일 로그인. 없는 이메일, 틀린 비밀번호, 탈퇴 회원, 소셜 가입 회원은 모두 같은 401 LOGIN_FAILED다.
     * 비밀번호가 맞은 뒤에만 정지 여부를 알려 준다(403 MEMBER_SUSPENDED, 사유·기한).
     * 같은 이메일로 15분 안에 5번 틀리면 15분 동안 429다(T055a, R-17). 가입하지 않은 이메일도 똑같이 세서
     * 막히는지로 가입 여부를 알 수 없다.
     */
    @Transactional(readOnly = true)
    public Member login(String rawEmail, String password) {
        return login(rawEmail, password, null);
    }

    /**
     * 로그인 (AUTH-01). deviceToken은 이 브라우저의 기기 쿠키 값이다(없으면 null).
     * 기기 쿠키가 이 이메일의 회원 것이면 틀린 횟수를 이메일이 아니라 그 기기로 센다(T055b, R-17). 그래서 다른 사람이 내 이메일로
     * 5번 틀려 이메일이 막혀도, 전에 로그인한 내 브라우저는 들어온다. 기기 쿠키가 없는 요청(공격자)은 지금처럼 이메일로 막힌다.
     * 기기도 15분에 5번 틀리면 막히고, IP별 제한은 그대로다.
     */
    public Member login(String rawEmail, String password, String deviceToken) {
        String email = Emails.normalize(rawEmail);
        Member member = attemptLimiter.attempt(attemptKey(email, deviceToken), () -> {
            Optional<Member> found = memberRepository.findByEmail(email);
            String hash = found.map(Member::getPasswordHash).orElse(null);
            boolean matches = passwordEncoder.matches(password, hash == null ? dummyPasswordHash : hash);
            if (hash == null || !matches || found.get().isWithdrawn()) {
                throw new BusinessException(ErrorCode.LOGIN_FAILED);
            }
            return found.get();
        }, e -> e.getErrorCode() == ErrorCode.LOGIN_FAILED);
        if (member.isSuspendedAt(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.MEMBER_SUSPENDED, suspensionDetails.of(member));
        }
        return member;
    }

    /** 믿을 수 있는 기기(이 이메일 회원의 기기 쿠키)면 기기별, 아니면 이메일별 횟수. */
    private String attemptKey(String email, String deviceToken) {
        return deviceTokens.verify(deviceToken)
                .filter(device -> memberRepository.findByEmail(email)
                        .map(member -> member.getId().equals(device.memberId()))
                        .orElse(false))
                .map(device -> "login-device:" + device.nonce())
                .orElse("login:" + email);
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
