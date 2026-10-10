package com.nhnacademy.blog.comment.domain;

import com.nhnacademy.blog.member.domain.Member;
import java.time.LocalDateTime;

/**
 * 댓글과 방명록이 함께 따르는 규칙의 대상 (CMT-04 "댓글과 같은 규칙"). 비밀·답글 한 단계·삭제된 자리 표시를
 * 둘 다 같은 코드(CommentViews)로 판단하려고, 두 엔티티가 이 모양을 드러낸다.
 */
public interface CommentEntry {

    Long getId();

    /** 답글이면 부모의 id, 최상위면 null. */
    Long getParentId();

    Member getMember();

    String getContent();

    boolean isSecret();

    /** 관리자가 숨겼나(ADMIN-03). 방명록에는 숨김이 없어 늘 false다. */
    boolean isBlinded();

    boolean isDeleted();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    default boolean isWrittenBy(Long memberId) {
        return memberId != null && memberId.equals(getMember().getId());
    }

}
