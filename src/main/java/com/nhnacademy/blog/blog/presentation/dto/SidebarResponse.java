package com.nhnacademy.blog.blog.presentation.dto;

import com.nhnacademy.blog.blog.application.Sidebar;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.presentation.dto.CategoryTreeResponse;
import com.nhnacademy.blog.tag.presentation.dto.TagCountResponse;
import java.util.List;

/**
 * 사이드바 응답 (contracts/rest-api.md 사이드바 응답). 보이는 모듈만 위에서부터 순서대로 { type, data }다.
 */
public record SidebarResponse(List<Module> modules) {

    public record Module(String type, Object data) {
    }

    public record Profile(String name, String description, String profileImageUrl) {
    }

    public record RecentPost(Long id, String title) {
    }

    public record RecentComment(Long id, Long postId, String content, String authorNickname, String state) {
    }

    public static SidebarResponse from(Sidebar sidebar) {
        Blog blog = sidebar.blog();
        return new SidebarResponse(List.of(
                new Module("PROFILE", new Profile(blog.getName(), blog.getDescription(), sidebar.profileImageUrl())),
                new Module("CATEGORY", CategoryTreeResponse.from(sidebar.categories())),
                new Module("TAG", sidebar.tags().stream().map(TagCountResponse::from).toList()),
                new Module("RECENT_POST", sidebar.recentPosts().stream()
                        .map(post -> new RecentPost(post.id(), post.title()))
                        .toList()),
                new Module("RECENT_COMMENT", sidebar.recentComments().stream()
                        .map(comment -> new RecentComment(comment.id(), comment.postId(), comment.content(),
                                comment.authorNickname(), comment.state().name()))
                        .toList())));
    }

}
