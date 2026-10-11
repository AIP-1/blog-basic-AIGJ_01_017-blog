package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.PasswordRule;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.member.domain.Member;
import java.util.function.Supplier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 본인 확인과 비밀번호 바꾸기 (인증의 입구, 스텝 19b). 회원 기능(비밀번호 변경 AUTH-05, 탈퇴 AUTH-06)은 비밀번호 해시나
 * 시도 횟수 제한을 직접 다루지 않고 이것만 부른다. 지금 비밀번호 확인은 어디서 하든 같은 횟수 제한(회원별 15분 5번, R-17)을
 * 같이 쓴다. 그래야 비밀번호 변경은 막혀도 탈퇴 화면으로 무작정 맞혀 보는 길이 생기지 않는다.
 * 스텝 20의 소셜 재인증(탈퇴용)도 여기에 더한다.
 */
@Service
public class CredentialService {

    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter attemptLimiter;

    public CredentialService(PasswordEncoder passwordEncoder, AttemptLimiter attemptLimiter) {
        this.passwordEncoder = passwordEncoder;
        this.attemptLimiter = attemptLimiter;
    }

    /** 이메일 가입 회원인가(비밀번호가 있나). 소셜로만 가입한 회원은 false. */
    public boolean hasPassword(Member member) {
        return member.getPasswordHash() != null;
    }

    /**
     * 지금 비밀번호가 맞는지 본다. 틀리면 mismatch가 만든 오류를 던지고 실패로 센다.
     * 15분 안에 5번 틀리면 15분 동안 429(맞는 값을 넣어도). 비밀번호가 없는 회원도 틀린 것으로 본다.
     */
    public void verifyCurrentPassword(Member member, String rawPassword, Supplier<BusinessException> mismatch) {
        attemptLimiter.attempt(key(member), () -> {
            if (rawPassword == null || member.getPasswordHash() == null
                    || !passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
                throw mismatch.get();
            }
            return null;
        }, e -> true);
    }

    /**
     * 비밀번호 바꾸기 (AUTH-05). 이메일 가입 회원만(아니면 403). 지금 비밀번호가 틀리면 400(currentPassword),
     * 새 비밀번호는 가입과 같은 규칙(PasswordRule, 400 newPassword).
     */
    public void changePassword(Member member, String currentPassword, String newPassword) {
        if (!hasPassword(member)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        verifyCurrentPassword(member, currentPassword,
                () -> BusinessException.invalidField("currentPassword", "지금 비밀번호가 맞지 않습니다."));
        PasswordRule.check("newPassword", newPassword);
        member.changePassword(passwordEncoder.encode(newPassword));
    }

    private static String key(Member member) {
        return "password-change:" + member.getId();
    }

}
