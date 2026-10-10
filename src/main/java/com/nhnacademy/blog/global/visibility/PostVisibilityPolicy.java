package com.nhnacademy.blog.global.visibility;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import org.springframework.stereotype.Component;

/**
 * 글 하나의 가시성 판단 (T011, COM-01, POST-04). 모든 글 상세 조회가 이것을 거친다.
 * <pre>
 * ① 글이 있나(글·블로그 삭제 안 됨)        아니오 → 404
 * ② 요청한 블로그 소속인가                 아니오 → ③④로 볼 수 있으면 301, 아니면 404
 * ③ 보는 사람이 블로그 주인인가            예 → 보임(숨긴 글이면 사유와 함께)
 * ④ 다른 사람이 볼 수 있나                 아니오 → 404, 구독만 안 한 구독자 공개 글이면 구독 안내
 * </pre>
 * 목록은 같은 ④를 쿼리 조건으로 바꾼 PostSpecifications를 쓴다.
 */
@Component
public class PostVisibilityPolicy {

    private final PostRepository postRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BlogVisibilityPolicy blogVisibilityPolicy;

    public PostVisibilityPolicy(PostRepository postRepository, SubscriptionRepository subscriptionRepository,
                                BlogVisibilityPolicy blogVisibilityPolicy) {
        this.postRepository = postRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
    }

    /**
     * @param postId      글 번호
     * @param requestBlog 요청 Host의 블로그. 어느 주소에서 불러도 같은 API(*)면 null
     * @param viewerId    보는 사람. 비회원이면 null
     */
    public PostAccess decide(Long postId, Blog requestBlog, Long viewerId) {
        return postRepository.findWithBlogById(postId)
                .map(post -> decide(post, requestBlog, viewerId))
                .orElseGet(PostAccess.NotFound::new);
    }

    /** post는 블로그와 블로그 주인이 읽혀 있어야 한다 (PostRepository.findWithBlogById). */
    public PostAccess decide(Post post, Blog requestBlog, Long viewerId) {
        Blog blog = post.getBlog();
        // ① 있나
        if (post.isDeleted() || blog.isDeleted()) {
            return new PostAccess.NotFound();
        }
        PostAccess access = decideInOwnBlog(post, viewerId);
        // ② 요청한 블로그 소속인가
        if (requestBlog != null && !requestBlog.getId().equals(blog.getId())) {
            return access.canRead() ? new PostAccess.MovedTo(blog) : new PostAccess.NotFound();
        }
        return access;
    }

    private PostAccess decideInOwnBlog(Post post, Long viewerId) {
        Blog blog = post.getBlog();
        // ③ 주인인가
        if (blog.isOwnedBy(viewerId)) {
            return new PostAccess.Owner(post, post.isBlinded());
        }
        // ④ 다른 사람이 볼 수 있나
        boolean openExceptAudience = post.getStatus() == PostStatus.PUBLISHED
                && !post.isBlinded()
                && (post.getCategory() == null || !post.getCategory().isHidden())   // 비공개 카테고리 (CAT-05)
                && blogVisibilityPolicy.isOpenToOthers(blog);
        if (!openExceptAudience) {
            return new PostAccess.NotFound();
        }
        return switch (post.getVisibility()) {
            case PUBLIC -> new PostAccess.Visible(post);
            case PRIVATE -> new PostAccess.NotFound();
            case SUBSCRIBERS -> isSubscribed(viewerId, blog)
                    ? new PostAccess.Visible(post)
                    : new PostAccess.SubscribersOnly(blog);
        };
    }

    private boolean isSubscribed(Long viewerId, Blog blog) {
        return viewerId != null && subscriptionRepository.existsByMemberIdAndBlogId(viewerId, blog.getId());
    }

}
