package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.SidebarModuleType;
import com.nhnacademy.blog.category.application.CategoryTree;
import com.nhnacademy.blog.tag.application.TagCount;
import java.util.List;

/**
 * 사이드바 (BLOG-04, BLOG-05). modules는 보이는 모듈만 주인이 정한 순서로. 숨긴 모듈의 데이터는 읽지 않아 null이다.
 */
public record Sidebar(Blog blog, List<SidebarModuleType> modules, String profileImageUrl, CategoryTree categories,
                      List<TagCount> tags, List<RecentPost> recentPosts, List<RecentComment> recentComments,
                      Visitor visitor, List<PopularPost> popularPosts, Subscribe subscribe) {

    public record RecentPost(Long id, String title) {
    }

    /** content·authorNickname은 state가 NORMAL일 때만 있다. */
    public record RecentComment(Long id, Long postId, String content, String authorNickname, CommentState state) {
    }

    /** 방문자 수 오늘·어제·누적. 누적은 어제까지 모은 값에 오늘을 더한 것이다. */
    public record Visitor(long today, long yesterday, long total) {
    }

    /** 인기 글: 누적 조회수 상위 5개(볼 수 있는 글만). */
    public record PopularPost(Long id, String title, long viewCount) {
    }

    /** 구독 버튼과 구독자 수. subscribed는 보는 사람 기준(비회원 false). */
    public record Subscribe(Long blogId, long subscriberCount, boolean subscribed) {
    }

    public enum CommentState {
        NORMAL,
        /** 비밀댓글. 사이드바에는 누구에게나 내용 없이 "비밀댓글입니다"로 보인다 (CMT-06). */
        SECRET,
        /** 관리자가 숨긴 댓글 (ADMIN-03). */
        BLINDED
    }

}
