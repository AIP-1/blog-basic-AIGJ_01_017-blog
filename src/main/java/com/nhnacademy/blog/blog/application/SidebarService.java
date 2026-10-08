package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.application.CategoryTreeService;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import jakarta.persistence.criteria.Join;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이드바 (T025, BLOG-04). 볼 수 없는 글과 그 글의 댓글은 빠진다(헌법 원칙 II).
 */
@Service
public class SidebarService {

    public static final int RECENT_SIZE = 5;

    private static final Sort LATEST_POSTS = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private static final Sort LATEST_COMMENTS = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CategoryTreeService categoryTreeService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final Clock clock;

    public SidebarService(CategoryTreeService categoryTreeService, PostRepository postRepository,
                          CommentRepository commentRepository, Clock clock) {
        this.categoryTreeService = categoryTreeService;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.clock = clock;
    }

    /** 댓글 작성자 닉네임처럼 지연 로딩되는 값은 이 트랜잭션 안에서 꺼내 기록(record)으로 넘긴다. */
    @Transactional(readOnly = true)
    public Sidebar sidebar(Blog blog, Long viewerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new Sidebar(blog, categoryTreeService.tree(blog, viewerId), recentPosts(blog, viewerId, now),
                recentComments(blog, viewerId, now));
    }

    private List<Sidebar.RecentPost> recentPosts(Blog blog, Long viewerId, LocalDateTime now) {
        return postRepository.findBy(PostSpecifications.listedIn(blog, viewerId, now),
                        query -> query.sortBy(LATEST_POSTS).limit(RECENT_SIZE).all())
                .stream()
                .map(post -> new Sidebar.RecentPost(post.getId(), post.getTitle()))
                .toList();
    }

    private List<Sidebar.RecentComment> recentComments(Blog blog, Long viewerId, LocalDateTime now) {
        Specification<Comment> condition = (root, query, cb) -> {
            Join<Comment, Post> post = root.join("post");
            return cb.and(cb.isNull(root.get("deletedAt")),
                    PostSpecifications.listedIn(post, query, cb, blog, viewerId, now));
        };
        return commentRepository.findBy(condition, query -> query.sortBy(LATEST_COMMENTS).limit(RECENT_SIZE).all())
                .stream()
                .map(SidebarService::toRecentComment)
                .toList();
    }

    private static Sidebar.RecentComment toRecentComment(Comment comment) {
        Sidebar.CommentState state = comment.isBlinded() ? Sidebar.CommentState.BLINDED
                : comment.isSecret() ? Sidebar.CommentState.SECRET
                : Sidebar.CommentState.NORMAL;
        boolean shown = state == Sidebar.CommentState.NORMAL;
        return new Sidebar.RecentComment(comment.getId(), comment.getPost().getId(),
                shown ? comment.getContent() : null, shown ? comment.getMember().getNickname() : null, state);
    }

}
