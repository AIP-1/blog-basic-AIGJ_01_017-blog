package com.nhnacademy.blog.admin.presentation.dto;

import com.nhnacademy.blog.admin.application.ModerationTargets;

/**
 * 관리 화면의 대상 한 줄. 화면은 blogAddress·postId로 링크를 만든다(글·댓글은 그 글, 블로그는 홈).
 * exists가 false면 지워진 대상, sanctioned는 지금 숨김·제한·정지 중인가.
 */
public record TargetResponse(String type, Long id, String label, String blogAddress, Long postId, Long memberId,
                             boolean exists, boolean sanctioned) {

    public static TargetResponse from(ModerationTargets.TargetView view) {
        return new TargetResponse(view.type().name(), view.id(), view.label(), view.blogAddress(), view.postId(),
                view.memberId(), view.exists(), view.sanctioned());
    }

}
