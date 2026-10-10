package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.Report;
import com.nhnacademy.blog.admin.domain.ReportRepository;
import com.nhnacademy.blog.admin.domain.ReportTargetType;
import com.nhnacademy.blog.admin.domain.SanctionReason;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import com.nhnacademy.blog.global.visibility.PostVisibilityPolicy;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고하기 (T110, ADMIN-04). 회원이 글·댓글·블로그를 사유와 함께 신고한다.
 * 상태 코드 순서: 대상 종류·번호가 틀림 400 → 볼 수 없는 대상 404 → 비회원 401 → 사유 400 → 이미 신고 409.
 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final CommentRepository commentRepository;
    private final BlogRepository blogRepository;
    private final PostVisibilityPolicy postVisibilityPolicy;
    private final BlogVisibilityPolicy blogVisibilityPolicy;

    public ReportService(ReportRepository reportRepository, CommentRepository commentRepository,
                         BlogRepository blogRepository, PostVisibilityPolicy postVisibilityPolicy,
                         BlogVisibilityPolicy blogVisibilityPolicy) {
        this.reportRepository = reportRepository;
        this.commentRepository = commentRepository;
        this.blogRepository = blogRepository;
        this.postVisibilityPolicy = postVisibilityPolicy;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
    }

    @Transactional
    public void report(LoginMember member, String rawTargetType, Long targetId, String rawReason, String rawDescription) {
        ReportTargetType targetType = parseTarget(rawTargetType);
        if (targetId == null) {
            throw BusinessException.invalidField("targetId", "신고할 대상을 골라 주세요.");
        }
        Long viewerId = member == null ? null : member.id();
        requireVisible(targetType, targetId, viewerId);
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        SanctionReason reason = Sanction.parseReason("reason", rawReason);
        String description = rawDescription == null || rawDescription.isBlank() ? null : rawDescription.strip();
        if (reason == SanctionReason.ETC && description == null) {
            throw BusinessException.invalidField("description", "기타를 고르면 설명을 써 주세요.");
        }
        if (description != null && description.length() > Report.DESCRIPTION_LENGTH) {
            throw BusinessException.invalidField("description", "설명은 " + Report.DESCRIPTION_LENGTH + "자까지입니다.");
        }
        int inserted = reportRepository.insertIfAbsent(member.id(), targetType.name(), targetId, reason.name(),
                description);
        if (inserted == 0) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
    }

    private static ReportTargetType parseTarget(String value) {
        return Arrays.stream(ReportTargetType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> BusinessException.invalidField("targetType", "POST, COMMENT, BLOG 중 하나입니다."));
    }

    /** 신고하는 사람이 볼 수 있는 대상인가. 아니면 있는지도 알리지 않고 404. */
    private void requireVisible(ReportTargetType type, Long id, Long viewerId) {
        boolean visible = switch (type) {
            case POST -> postVisibilityPolicy.decide(id, null, viewerId).canRead();
            case COMMENT -> commentRepository.findWithPostById(id)
                    .filter(comment -> !comment.isDeleted())
                    .filter(comment -> postVisibilityPolicy.decide(comment.getPost(), null, viewerId).canRead())
                    .filter(comment -> canSeeContent(comment, viewerId))
                    .isPresent();
            case BLOG -> blogRepository.findWithMemberById(id)
                    .filter(blog -> blogVisibilityPolicy.canView(blog, viewerId) && !blog.isMoved())
                    .isPresent();
        };
        if (!visible) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    /** 비밀댓글은 글 주인과 작성자만 내용을 본다(CMT-06). 숨긴 댓글은 이미 숨겨져 있어 신고할 것이 없다. */
    private static boolean canSeeContent(Comment comment, Long viewerId) {
        if (comment.isBlinded()) {
            return false;
        }
        if (!comment.isSecret()) {
            return true;
        }
        return viewerId != null && (viewerId.equals(comment.getMember().getId())
                || comment.getPost().getBlog().isOwnedBy(viewerId));
    }

}
