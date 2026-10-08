package com.nhnacademy.blog.comment.application;

import com.nhnacademy.blog.comment.domain.Comment;
import java.util.Map;

/**
 * 보는 사람 기준의 댓글 한 개 (contracts/rest-api.md Comment).
 *
 * @param state                    NORMAL, SECRET(비밀댓글을 남이 봄), BLINDED(관리자가 숨김, 작성자 아닌 사람)
 * @param authorPrimaryBlogAddress 작성자의 대표 블로그 주소. 없거나 볼 수 없으면 null
 * @param blind                    작성자가 자기 숨긴 댓글을 볼 때만 사유
 */
public record CommentView(Comment comment, State state, String authorPrimaryBlogAddress, boolean canDelete,
                          Map<String, String> blind) {

    public enum State {
        NORMAL,
        SECRET,
        BLINDED
    }

    /** 내용과 작성자를 보여 줘도 되는가. */
    public boolean showsContent() {
        return state == State.NORMAL;
    }

}
