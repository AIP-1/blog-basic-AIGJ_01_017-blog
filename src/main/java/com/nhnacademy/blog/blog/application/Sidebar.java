package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.application.CategoryTree;
import java.util.List;

/**
 * 사이드바 (BLOG-04). 지금은 기본 구성 순서로 블로그 홈 바로가기(이름·소개), 카테고리, 최근 글, 최근 댓글이다.
 * 태그(TAG-03)는 태그 기능(스텝 7), 순서·표시 설정과 방문자·인기 글·구독 모듈(BLOG-05)은 T086에서 더한다.
 */
public record Sidebar(Blog blog, CategoryTree categories, List<RecentPost> recentPosts,
                      List<RecentComment> recentComments) {

    public record RecentPost(Long id, String title) {
    }

    /** content·authorNickname은 state가 NORMAL일 때만 있다. */
    public record RecentComment(Long id, Long postId, String content, String authorNickname, CommentState state) {
    }

    public enum CommentState {
        NORMAL,
        /** 비밀댓글. 사이드바에는 누구에게나 내용 없이 "비밀댓글입니다"로 보인다 (CMT-06). */
        SECRET,
        /** 관리자가 숨긴 댓글 (ADMIN-03). */
        BLINDED
    }

}
