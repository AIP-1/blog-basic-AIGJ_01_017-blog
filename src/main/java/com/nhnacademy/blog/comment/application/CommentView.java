package com.nhnacademy.blog.comment.application;

import com.nhnacademy.blog.comment.domain.CommentEntry;
import java.util.List;
import java.util.Map;

/**
 * 보는 사람 기준의 댓글·방명록 한 개 (contracts/rest-api.md Comment). 댓글과 방명록이 같은 모양으로 나간다.
 *
 * @param entry                    댓글(Comment) 또는 방명록(Guestbook)
 * @param state                    NORMAL, SECRET(비밀글을 남이 봄), DELETED(지웠지만 답글이 있음), BLINDED(관리자가 숨김)
 * @param authorPrimaryBlogAddress 작성자의 대표 블로그 주소. 없거나 볼 수 없으면 null
 * @param authorProfileImageUrl    작성자의 회원 프로필 사진 썸네일 주소. 없으면 null
 * @param canEdit                  고칠 수 있나: 작성자 본인이고, 지우거나 숨긴 것이 아님 (CMT-03, CMT-04)
 * @param canDelete                지울 수 있나: 작성자 본인 또는 블로그 주인 (CMT-01, CMT-02)
 * @param blind                    작성자가 자기 숨긴 댓글을 볼 때만 사유
 * @param replies                  답글(CMT-05, 작성순). 답글 자신은 빈 목록이다
 */
public record CommentView(CommentEntry entry, State state, String authorPrimaryBlogAddress,
                          String authorProfileImageUrl, boolean canEdit, boolean canDelete,
                          Map<String, String> blind, List<CommentView> replies) {

    public enum State {
        NORMAL,
        SECRET,
        /** 지웠지만 답글이 있어 자리만 남김 (CMT-05). "삭제된 댓글입니다" */
        DELETED,
        BLINDED
    }

    /** 답글을 붙인 새 값. 답글에는 답글이 없다(한 단계). */
    public CommentView withReplies(List<CommentView> children) {
        return new CommentView(entry, state, authorPrimaryBlogAddress, authorProfileImageUrl, canEdit, canDelete,
                blind, children);
    }

    /** 내용과 작성자를 보여 줘도 되는가. */
    public boolean showsContent() {
        return state == State.NORMAL;
    }

}
