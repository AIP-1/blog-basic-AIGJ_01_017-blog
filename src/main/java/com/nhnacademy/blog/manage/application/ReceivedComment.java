package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.comment.application.CommentView;

/**
 * 관리 화면의 받은 댓글·방명록 한 줄 (MNG-02).
 *
 * @param postId    댓글이 달린 글. 방명록이면 null
 * @param postTitle 그 글의 제목. 방명록이면 null
 */
public record ReceivedComment(CommentView view, Long postId, String postTitle) {
}
