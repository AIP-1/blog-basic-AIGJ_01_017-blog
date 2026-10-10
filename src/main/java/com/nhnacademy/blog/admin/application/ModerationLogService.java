package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.web.PageQuery;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리 이력 검색 (T112, ADMIN-06). 조회만 된다(고치거나 지우는 API가 없다). 대상 종류·번호, 관리자, 기간(날짜, 양 끝 포함)으로 거른다.
 */
@Service
public class ModerationLogService {

    public static final int PAGE_SIZE = 20;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ModerationLogRepository moderationLogRepository;
    private final ModerationTargets moderationTargets;

    public ModerationLogService(ModerationLogRepository moderationLogRepository, ModerationTargets moderationTargets) {
        this.moderationLogRepository = moderationLogRepository;
        this.moderationTargets = moderationTargets;
    }

    @Transactional(readOnly = true)
    public Page<LogLine> search(String rawTargetType, Long targetId, Long adminId, String from, String to,
                                PageQuery page) {
        Specification<ModerationLog> condition = Specification.unrestricted();
        if (rawTargetType != null && !rawTargetType.isBlank()) {
            ModerationTargetType type = Arrays.stream(ModerationTargetType.values())
                    .filter(value -> value.name().equals(rawTargetType)).findFirst()
                    .orElseThrow(() -> BusinessException.invalidField("targetType", "알 수 없는 대상 종류입니다."));
            condition = condition.and((root, query, cb) -> cb.equal(root.get("targetType"), type));
        }
        if (targetId != null) {
            condition = condition.and((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }
        if (adminId != null) {
            condition = condition.and((root, query, cb) -> cb.equal(root.get("admin").get("id"), adminId));
        }
        LocalDate fromDate = date("from", from);
        LocalDate toDate = date("to", to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw BusinessException.invalidField("to", "끝 날짜가 시작 날짜보다 앞입니다.");
        }
        if (fromDate != null) {
            condition = condition.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            condition = condition.and((root, query, cb) ->
                    cb.lessThan(root.get("createdAt"), toDate.plusDays(1).atStartOfDay()));
        }
        return moderationLogRepository.findAll(condition, page.toPageable(NEWEST)).map(this::line);
    }

    /** 대시보드의 최근 조치 5개. */
    @Transactional(readOnly = true)
    public List<LogLine> recent() {
        return moderationLogRepository.findRecent().stream().map(this::line).toList();
    }

    /** 회원 상세의 제재 이력. */
    @Transactional(readOnly = true)
    public List<LogLine> aboutMember(Long memberId) {
        return moderationLogRepository.findAboutMember(memberId).stream().map(this::line).toList();
    }

    private LogLine line(ModerationLog log) {
        return new LogLine(log, log.getAdmin().getNickname(), moderationTargets.of(log.getTargetType(), log.getTargetId()));
    }

    private static LocalDate date(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw BusinessException.invalidField(field, "날짜는 2026-10-11 꼴입니다.");
        }
    }

    public record LogLine(ModerationLog log, String adminNickname, ModerationTargets.TargetView target) {
    }

}
