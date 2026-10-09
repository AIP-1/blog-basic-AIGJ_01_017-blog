package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.EmailVerification;
import com.nhnacademy.blog.auth.domain.EmailVerificationRepository;
import com.nhnacademy.blog.auth.domain.Emails;
import com.nhnacademy.blog.global.auth.AttemptLimiter;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.RetryAfterDetail;
import com.nhnacademy.blog.member.domain.MemberRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증 코드 (T017, OWN-01). 6자리 숫자, 10분 유효, 같은 이메일로 1분 안에 다시 보내면 429.
 * <ul>
 *   <li>send: 코드를 만들어 저장하고 메일로 보낸다.</li>
 *   <li>verify: 화면에서 바로 결과를 보여 주려고 확인만 한다. 코드를 쓰지 않는다.</li>
 *   <li>consume: 가입할 때 서버가 다시 확인하고 verified_at을 남긴다. 같은 코드로 두 번 가입할 수 없다.</li>
 * </ul>
 */
@Service
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final String COOLDOWN_PREFIX = "email-verification:cooldown:";

    private final EmailVerificationRepository verificationRepository;
    private final MemberRepository memberRepository;
    private final EmailSender emailSender;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final AttemptLimiter attemptLimiter;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationRepository verificationRepository,
                                    MemberRepository memberRepository, EmailSender emailSender,
                                    StringRedisTemplate redis, Clock clock, AttemptLimiter attemptLimiter) {
        this.verificationRepository = verificationRepository;
        this.memberRepository = memberRepository;
        this.emailSender = emailSender;
        this.redis = redis;
        this.clock = clock;
        this.attemptLimiter = attemptLimiter;
    }

    @Transactional
    public void send(String rawEmail) {
        String email = Emails.normalize(rawEmail);
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN);
        }
        // 1분 안에 다시 요청하면 거절한다. SET NX라 동시에 두 번 와도 하나만 통과한다
        String cooldownKey = COOLDOWN_PREFIX + email;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldownKey, "1", RESEND_INTERVAL))) {
            Long seconds = redis.getExpire(cooldownKey);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    new RetryAfterDetail(seconds == null || seconds < 1 ? 1 : seconds));
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        verificationRepository.save(EmailVerification.issue(email, code, LocalDateTime.now(clock).plus(CODE_TTL)));
        emailSender.send(email, "[블로그] 이메일 인증 코드",
                "인증 코드: " + code + "\n" + CODE_TTL.toMinutes() + "분 안에 가입 화면에 입력해 주세요.");
    }

    @Transactional(readOnly = true)
    public void verify(String rawEmail, String code) {
        check(Emails.normalize(rawEmail), code);
    }

    /** 가입 트랜잭션 안에서 부른다. 가입이 실패하면 함께 되돌아가 코드를 다시 쓸 수 있다. */
    @Transactional
    public void consume(String rawEmail, String code) {
        check(Emails.normalize(rawEmail), code).markVerified(LocalDateTime.now(clock));
    }

    /**
     * 가장 최근 코드만 본다. 틀렸거나 이미 썼으면 INVALID, 맞지만 시간이 지났으면 EXPIRED.
     * 같은 이메일로 15분 안에 5번 틀리면(INVALID) 15분 동안 429다(T055a, R-17). 6자리 코드를 계속 넣어 맞히지 못하게 한다.
     * 코드를 새로 받아도 틀린 횟수는 그대로다(새로 받기로 제한을 풀 수 없게).
     */
    private EmailVerification check(String email, String code) {
        return attemptLimiter.attempt("email-code:" + email, () -> {
            EmailVerification latest = verificationRepository.findFirstByEmailOrderByCreatedAtDescIdDesc(email)
                    .filter(verification -> !verification.isUsed() && verification.matches(code))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
            if (latest.isExpiredAt(LocalDateTime.now(clock))) {
                throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
            }
            return latest;
        }, e -> e.getErrorCode() == ErrorCode.INVALID_VERIFICATION_CODE);
    }

}
