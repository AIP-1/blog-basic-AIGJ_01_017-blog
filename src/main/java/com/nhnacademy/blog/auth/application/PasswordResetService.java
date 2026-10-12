package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.Emails;
import com.nhnacademy.blog.auth.domain.PasswordResetToken;
import com.nhnacademy.blog.auth.domain.PasswordResetTokenRepository;
import com.nhnacademy.blog.auth.domain.PasswordRule;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.RetryAfterDetail;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정과 메일로 잠금 풀기 (T088, OWN-02, R-17).
 * <ul>
 *   <li>request: 재설정 링크를 메일로 보낸다. 가입하지 않은 이메일이어도 같은 응답(가입 여부를 드러내지 않음).</li>
 *   <li>noticeLocked: 로그인이 이메일 때문에 막히면 그 이메일로 "본인이면 이 링크로" 메일을 보낸다.
 *       남이 일부러 틀려 내 로그인을 막아도, 본인은 메일의 링크로 비밀번호를 바꾸고 바로 들어온다.</li>
 *   <li>reset: 링크의 토큰으로 새 비밀번호를 정한다. 30분 안에 한 번만. 성공하면 그 이메일의 로그인 잠금도 푼다.</li>
 * </ul>
 * 링크 토큰은 추측할 수 없는 임의 값(256비트)이고 DB에는 SHA-256 해시만 둔다.
 * 메일 폭탄을 막으려고 재설정 메일은 같은 이메일로 1분에 한 번(429), 잠금 안내 메일은 15분에 한 번만 보낸다.
 */
@Service
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final String COOLDOWN_PREFIX = "password-reset:cooldown:";
    private static final String LOCK_NOTICE_PREFIX = "password-reset:lock-notice:";

    private final PasswordResetTokenRepository tokenRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter attemptLimiter;
    private final AttemptLimitProperties attemptLimitProperties;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, MemberRepository memberRepository,
                                PasswordEncoder passwordEncoder, AttemptLimiter attemptLimiter,
                                AttemptLimitProperties attemptLimitProperties, StringRedisTemplate redis,
                                ApplicationEventPublisher events, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.attemptLimiter = attemptLimiter;
        this.attemptLimitProperties = attemptLimitProperties;
        this.redis = redis;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 재설정 메일. linkBase는 토큰을 붙일 화면 주소(예: http://blog.com/password-reset?token=)다.
     * 1분 제한은 가입 여부와 상관없이 같은 이메일이면 건다. 가입한 이메일에만 걸면 429로 가입 여부를 알 수 있다.
     */
    @Transactional
    public void request(String rawEmail, String linkBase) {
        String email = Emails.normalize(rawEmail);
        String cooldownKey = COOLDOWN_PREFIX + email;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldownKey, "1", RESEND_INTERVAL))) {
            Long seconds = redis.getExpire(cooldownKey);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    new RetryAfterDetail(seconds == null || seconds < 1 ? 1 : seconds));
        }
        resettable(email).ifPresent(member -> send(member, linkBase, "[블로그] 비밀번호 재설정", """
                비밀번호 재설정을 요청하셨습니다.
                아래 링크를 %d분 안에 열어 새 비밀번호를 정해 주세요. 링크는 한 번만 쓸 수 있습니다.

                %%s

                요청하지 않으셨다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(TOKEN_TTL.toMinutes())));
    }

    /**
     * 이메일 때문에 로그인이 막혔다는 안내 (R-17 ②). 같은 이메일에는 잠금 시간(15분)에 한 번만 보낸다.
     * 가입하지 않은 이메일이면 아무것도 하지 않는다. 응답은 부르는 쪽(로그인)이 정하므로 가입 여부가 드러나지 않는다.
     */
    @Transactional
    public void noticeLocked(String rawEmail, String linkBase) {
        String email = Emails.normalize(rawEmail);
        Optional<Member> member = resettable(email);
        if (member.isEmpty() || !Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(LOCK_NOTICE_PREFIX + email, "1", attemptLimitProperties.window()))) {
            return;
        }
        send(member.get(), linkBase, "[블로그] 로그인이 잠시 막혔습니다", """
                이 이메일로 로그인에 여러 번 실패해 %d분 동안 로그인이 막혔습니다.
                본인이라면 아래 링크로 비밀번호를 새로 정하면 바로 로그인할 수 있습니다(%d분 안에, 한 번만).

                %%s

                본인이 시도한 것이 아니라면 다른 사람이 비밀번호를 맞혀 보고 있을 수 있습니다.
                비밀번호는 바뀌지 않았고, 이 메일은 무시해도 됩니다.
                """.formatted(attemptLimitProperties.window().toMinutes(), TOKEN_TTL.toMinutes()));
    }

    /**
     * 새 비밀번호 정하기. 토큰이 없거나 틀렸거나 30분 지났거나 이미 썼으면 400 RESET_TOKEN_INVALID,
     * 새 비밀번호가 규칙에 맞지 않으면 400(newPassword)이고 이때는 링크를 쓰지 않은 것으로 남는다.
     * 성공하면 그 회원에게 보낸 다른 링크도 모두 못 쓰게 하고, 그 이메일의 로그인 잠금을 푼다.
     */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = Optional.ofNullable(rawToken)
                .filter(value -> !value.isBlank())
                .flatMap(value -> tokenRepository.findByTokenHashForUpdate(hash(value)))
                .filter(found -> found.isUsableAt(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));
        Member member = memberRepository.findById(token.getMemberId())
                .filter(found -> !found.isWithdrawn() && found.getPasswordHash() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));
        PasswordRule.check("newPassword", newPassword);

        member.changePassword(passwordEncoder.encode(newPassword));
        tokenRepository.markAllUsed(member.getId(), now);
        attemptLimiter.clear("login:" + member.getEmail());
    }

    /** 비밀번호로 로그인하는 활동 회원. 소셜로만 가입한 회원은 email이 없어 찾히지 않는다. */
    private Optional<Member> resettable(String email) {
        return memberRepository.findByEmail(email)
                .filter(member -> !member.isWithdrawn() && member.getPasswordHash() != null);
    }

    /**
     * 토큰을 만들어 해시만 저장하고, 원문은 링크에 넣어 메일로만 보낸다. 메일은 커밋 뒤 다른 스레드에서 간다(ResetMailListener).
     * 요청이 SMTP를 기다리지 않아야 응답 시간으로 가입 여부가 드러나지 않는다.
     */
    private void send(Member member, String linkBase, String subject, String template) {
        String token = newToken();
        tokenRepository.save(PasswordResetToken.issue(member.getId(), hash(token),
                LocalDateTime.now(clock).plus(TOKEN_TTL)));
        events.publishEvent(new ResetMailRequested(member.getId(), member.getEmail(), subject,
                template.formatted(linkBase + token)));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
