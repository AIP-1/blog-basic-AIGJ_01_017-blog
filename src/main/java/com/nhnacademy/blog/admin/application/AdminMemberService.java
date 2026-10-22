package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.ReportRepository;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.auth.SuspensionDetail;
import com.nhnacademy.blog.global.auth.SuspensionDetails;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.LikePatterns;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.member.domain.MemberStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 조회 (T094, ADMIN-02). 상태는 지금 기준이다: 정지 기간이 지난 회원은 DB에 SUSPENDED로 남아 있어도 활동(ACTIVE)이다.
 */
@Service
public class AdminMemberService {

    // JPA 조건에 쓰는 엔티티 필드 이름
    private static final String STATUS = "status";
    private static final String SUSPENDED_UNTIL = "suspendedUntil";
    /** 오류 응답의 칸 이름(fieldErrors[].field). 값은 같지만 위 STATUS(엔티티 필드)와 뜻이 다르다 */
    private static final String STATUS_FIELD = "status";

    public static final int PAGE_SIZE = 20;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("id"));

    private final MemberRepository memberRepository;
    private final BlogRepository blogRepository;
    private final ReportRepository reportRepository;
    private final ModerationLogService moderationLogService;
    private final SuspensionDetails suspensionDetails;
    private final Clock clock;

    public AdminMemberService(MemberRepository memberRepository, BlogRepository blogRepository,
                              ReportRepository reportRepository, ModerationLogService moderationLogService,
                              SuspensionDetails suspensionDetails, Clock clock) {
        this.memberRepository = memberRepository;
        this.blogRepository = blogRepository;
        this.reportRepository = reportRepository;
        this.moderationLogService = moderationLogService;
        this.suspensionDetails = suspensionDetails;
        this.clock = clock;
    }

    /** q: 이메일·닉네임에 들어 있는 글자(대소문자 무시). status: ACTIVE·SUSPENDED·WITHDRAWN, 비면 전체. 가입 최신순 20. */
    @Transactional(readOnly = true)
    public Page<MemberRow> search(String q, String status, PageQuery page) {
        LocalDateTime now = LocalDateTime.now(clock);
        Specification<Member> condition = Specification.unrestricted();
        if (q != null && !q.isBlank()) {
            String pattern = LikePatterns.contains(q.strip());
            condition = condition.and((root, query, cb) -> cb.or(
                    LikePatterns.containsIgnoreCase(cb, root.get("email"), pattern),
                    LikePatterns.containsIgnoreCase(cb, root.get("nickname"), pattern)));
        }
        if (status != null && !status.isBlank()) {
            condition = condition.and(status(status, now));
        }
        return memberRepository.findAll(condition, page.toPageable(NEWEST)).map(member -> row(member, now));
    }

    @Transactional(readOnly = true)
    public MemberDetail detail(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        MemberRow row = row(member, now);
        SuspensionDetail suspension = member.isSuspendedAt(now) ? suspensionDetails.of(member) : null;
        return new MemberDetail(row, blogRepository.findByMemberIdOrderById(memberId), reportRepository
                .countReceivedByMember(memberId), suspension, moderationLogService.aboutMember(memberId));
    }

    private MemberRow row(Member member, LocalDateTime now) {
        return new MemberRow(member, effectiveStatus(member, now));
    }

    /** 정지 기간이 지났으면 활동이다. */
    static MemberStatus effectiveStatus(Member member, LocalDateTime now) {
        if (member.getStatus() == MemberStatus.SUSPENDED && !member.isSuspendedAt(now)) {
            return MemberStatus.ACTIVE;
        }
        return member.getStatus();
    }

    private static Specification<Member> status(String value, LocalDateTime now) {
        return switch (value) {
            case "ACTIVE" -> (root, query, cb) -> cb.or(
                    cb.equal(root.get(STATUS), MemberStatus.ACTIVE),
                    cb.and(cb.equal(root.get(STATUS), MemberStatus.SUSPENDED),
                            cb.lessThanOrEqualTo(root.get(SUSPENDED_UNTIL), now)));
            case "SUSPENDED" -> (root, query, cb) -> cb.and(
                    cb.equal(root.get(STATUS), MemberStatus.SUSPENDED),
                    cb.or(cb.isNull(root.get(SUSPENDED_UNTIL)), cb.greaterThan(root.get(SUSPENDED_UNTIL), now)));
            case "WITHDRAWN" -> (root, query, cb) -> cb.equal(root.get(STATUS), MemberStatus.WITHDRAWN);
            default -> throw BusinessException.invalidField(STATUS_FIELD, "ACTIVE, SUSPENDED, WITHDRAWN 중 하나입니다.");
        };
    }

    public record MemberRow(Member member, MemberStatus status) {
    }

    public record MemberDetail(MemberRow row, List<Blog> blogs, long reportCount, SuspensionDetail suspension,
                               List<ModerationLogService.LogLine> moderations) {
    }

}
