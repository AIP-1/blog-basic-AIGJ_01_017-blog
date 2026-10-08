package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.member.domain.Member;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * 403 MEMBER_SUSPENDED에 담을 정지 사유와 기한 (ADMIN-02).
 * 사유는 회원 행이 아니라 moderation_log의 최신 SUSPEND 행에서 읽는다.
 * 로그인 API와, 이미 로그인한 회원을 막는 JWT 필터가 같이 쓴다.
 */
@Component
public class SuspensionDetails {

    private final ModerationLogRepository moderationLogRepository;
    private final Clock clock;

    public SuspensionDetails(ModerationLogRepository moderationLogRepository, Clock clock) {
        this.moderationLogRepository = moderationLogRepository;
        this.clock = clock;
    }

    public SuspensionDetail of(Member member) {
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
