package com.nhnacademy.blog.blog.presentation.dto;

import com.nhnacademy.blog.blog.application.Sidebar;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.SidebarModuleType;
import com.nhnacademy.blog.category.presentation.dto.CategoryTreeResponse;
import com.nhnacademy.blog.tag.presentation.dto.TagCountResponse;
import java.util.ArrayList;
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

    public record PopularPost(Long id, String title, long viewCount) {
    }

    /** 보이는 모듈만 주인이 정한 순서로. 모듈마다 data 모양은 contracts 사이드바 응답과 같다. */
    public static SidebarResponse from(Sidebar sidebar) {
        Blog blog = sidebar.blog();
        List<Module> modules = new ArrayList<>();
        for (SidebarModuleType type : sidebar.modules()) {
            Object data = switch (type) {
                case PROFILE -> new Profile(blog.getName(), blog.getDescription(), sidebar.profileImageUrl());
                case CATEGORY -> CategoryTreeResponse.from(sidebar.categories());
                case TAG -> sidebar.tags().stream().map(TagCountResponse::from).toList();
                case RECENT_POST -> sidebar.recentPosts().stream()
                        .map(post -> new RecentPost(post.id(), post.title()))
                        .toList();
                case RECENT_COMMENT -> sidebar.recentComments().stream()
                        .map(comment -> new RecentComment(comment.id(), comment.postId(), comment.content(),
                                comment.authorNickname(), comment.state().name()))
                        .toList();
                case VISITOR -> sidebar.visitor();
                case POPULAR_POST -> sidebar.popularPosts().stream()
                        .map(post -> new PopularPost(post.id(), post.title(), post.viewCount()))
                        .toList();
                case SUBSCRIBE -> sidebar.subscribe();
            };
            modules.add(new Module(type.name(), data));
        }
        return new SidebarResponse(modules);
    }

}
