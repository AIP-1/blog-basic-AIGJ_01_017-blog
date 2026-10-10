package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import com.nhnacademy.blog.post.application.PostView;
import com.nhnacademy.blog.post.domain.Post;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * PostDetail (contracts/rest-api.md 주요 응답 객체). GET /api/posts/{id}.
 * viewer.bookmarked는 저장(백로그) 전이라 false다.
 * updatedAt은 발행 뒤 고친 적이 없으면 null이다.
 */
public record PostDetailResponse(Long id, BlogRef blog, String title, String contentHtml, CategoryRef category,
                                 List<String> tags, String topic, String visibility, OffsetDateTime publishedAt,
                                 OffsetDateTime updatedAt, long viewCount, int likeCount, int commentCount,
                                 boolean commentAllowed, MemberSummaryResponse author, Viewer viewer,
                                 Map<String, String> blind, Neighbor prev, Neighbor next) {

    public record BlogRef(Long id, String address, String name) {
    }

    public record CategoryRef(Long id, String name) {
    }

    public record Viewer(boolean isOwner, boolean liked, boolean bookmarked) {
    }

    public record Neighbor(Long id, String title) {
    }

    /** liked: 보는 사람이 이 글에 공감했나(SOC-01). 공감 기능(reaction)이 알려 준다. */
    public static PostDetailResponse from(PostView view, boolean liked) {
        Post post = view.post();
        Blog blog = post.getBlog();
        Category category = post.getCategory();
        return new PostDetailResponse(post.getId(), new BlogRef(blog.getId(), blog.getAddress(), blog.getName()),
                post.getTitle(), post.getContentHtml(),
                category == null ? null : new CategoryRef(category.getId(), category.getName()), view.tagNames(),
                post.getTopic() == null ? null : post.getTopic().name(), post.getVisibility().name(),
                DateTimes.toOffset(post.getPublishedAt()), DateTimes.toOffset(editedAt(post)), post.getViewCount(),
                post.getLikeCount(), post.getCommentCount(), post.isCommentAllowed(),
                MemberSummaryResponse.of(blog.getMember(), view.authorPrimaryBlogAddress(),
                        view.authorProfileImageUrl()),
                new Viewer(view.owner(), liked, false), view.blind(), neighbor(view.prev()), neighbor(view.next()));
    }

    /**
     * 처음 저장할 때 Auditing은 생성·수정 시각에 같은 값을 넣는다. 둘이 다르면 그 뒤에 고친 것이다.
     * 카테고리 삭제·댓글 수 갱신처럼 작성자가 고치지 않은 변경은 updated_at을 건드리지 않는다(PostRepository).
     */
    private static LocalDateTime editedAt(Post post) {
        return post.getUpdatedAt() == null || post.getUpdatedAt().equals(post.getCreatedAt())
                ? null : post.getUpdatedAt();
    }

    private static Neighbor neighbor(PostView.Neighbor neighbor) {
        return neighbor == null ? null : new Neighbor(neighbor.id(), neighbor.title());
    }

}
