package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.blog.application.PrimaryBlogAddresses;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.PostAccess;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.visibility.PostVisibilityPolicy;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 상세 (T041, POST-04)와 이전·다음 글 (T057, POST-10).
 * 볼 수 있는지는 PostVisibilityPolicy 하나로 판단한다. 볼 수 없으면 로그인 여부와 상관없이 404(헌법 원칙 II),
 * 구독자 공개 글을 구독하지 않은 사람이 열면 제목·본문 없이 403 SUBSCRIBERS_ONLY(Q4).
 */
@Service
public class PostReadService {

    private final PostRepository postRepository;
    private final PostVisibilityPolicy postVisibilityPolicy;
    private final PostService postService;
    private final PrimaryBlogAddresses primaryBlogAddresses;
    private final Clock clock;

    public PostReadService(PostRepository postRepository, PostVisibilityPolicy postVisibilityPolicy,
                           PostService postService, PrimaryBlogAddresses primaryBlogAddresses, Clock clock) {
        this.postRepository = postRepository;
        this.postVisibilityPolicy = postVisibilityPolicy;
        this.postService = postService;
        this.primaryBlogAddresses = primaryBlogAddresses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PostView detail(Blog blog, Long postId, Long viewerId) {
        Post post = readable(blog, postId, viewerId);
        boolean owner = blog.isOwnedBy(viewerId);
        Map<String, String> blind = owner && post.isBlinded() ? postService.blindReason(post) : null;
        return new PostView(post, owner, primaryBlogAddresses.ofOwner(blog, viewerId), blind,
                neighbor(blog, post, viewerId, false), neighbor(blog, post, viewerId, true));
    }

    /**
     * 보는 사람이 읽을 수 있는 이 블로그의 글. 댓글 API도 이것으로 글을 확인한다.
     * 블로그 주소 API라 다른 블로그 글 번호는 301이 아니라 404다(301은 화면 주소 단계에서만).
     */
    @Transactional(readOnly = true)
    public Post readable(Blog blog, Long postId, Long viewerId) {
        return switch (postVisibilityPolicy.decide(postId, blog, viewerId)) {
            case PostAccess.Owner owner -> owner.post();
            case PostAccess.Visible visible -> visible.post();
            case PostAccess.SubscribersOnly subscribersOnly -> throw new BusinessException(ErrorCode.SUBSCRIBERS_ONLY,
                    Map.of("blogId", blog.getId(), "blogName", blog.getName(), "blogAddress", blog.getAddress()));
            case PostAccess.NotFound notFound -> throw new BusinessException(ErrorCode.NOT_FOUND);
            case PostAccess.MovedTo movedTo -> throw new BusinessException(ErrorCode.NOT_FOUND);
        };
    }

    /**
     * 같은 블로그 발행 순서(처음 발행 시각, 같으면 id)에서 바로 앞(newer=false)이나 뒤(newer=true)의 글.
     * 목록과 같은 조건(listedIn)이라 볼 수 없는 글은 건너뛴다. 발행 전 글(임시저장 등)에는 이웃이 없다.
     */
    private PostView.Neighbor neighbor(Blog blog, Post post, Long viewerId, boolean newer) {
        LocalDateTime publishedAt = post.getPublishedAt();
        if (publishedAt == null) {
            return null;
        }
        Specification<Post> side = (root, query, cb) -> newer
                ? cb.or(cb.greaterThan(root.get("publishedAt"), publishedAt),
                        cb.and(cb.equal(root.get("publishedAt"), publishedAt), cb.greaterThan(root.get("id"), post.getId())))
                : cb.or(cb.lessThan(root.get("publishedAt"), publishedAt),
                        cb.and(cb.equal(root.get("publishedAt"), publishedAt), cb.lessThan(root.get("id"), post.getId())));
        Sort order = newer
                ? Sort.by(Sort.Order.asc("publishedAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        return postRepository.findBy(PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock)).and(side),
                        query -> query.sortBy(order).limit(1).all())
                .stream()
                .findFirst()
                .map(found -> new PostView.Neighbor(found.getId(), found.getTitle()))
                .orElse(null);
    }

}
