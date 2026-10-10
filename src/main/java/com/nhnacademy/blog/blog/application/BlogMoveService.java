package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.post.application.PostContentChangedEvent;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.tag.application.TagService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 이사 (T106, BLOG-06). 글을 같은 회원의 다른 활성 블로그로 옮기고, 옛 블로그에 이사 대상을 정한다.
 * 글 번호는 그대로라 옛 주소(a.blog.com/15)는 화면 주소 단계에서 글이 지금 있는 블로그(b.blog.com/15)로 301된다(스텝 2부터).
 * 주인 검사는 컨트롤러가 먼저 했다.
 */
@Service
public class BlogMoveService {

    public static final int MAX_POSTS = 100;

    private final BlogRepository blogRepository;
    private final PostRepository postRepository;
    private final TagService tagService;
    private final ApplicationEventPublisher events;

    public BlogMoveService(BlogRepository blogRepository, PostRepository postRepository, TagService tagService,
                           ApplicationEventPublisher events) {
        this.blogRepository = blogRepository;
        this.postRepository = postRepository;
        this.tagService = tagService;
        this.events = events;
    }

    /**
     * 고른 글을 대상 블로그로 옮긴다. 카테고리는 미분류, 태그는 이름으로 대상 블로그의 태그에 다시 연결(없으면 만든다).
     * 이 블로그의 지우지 않은 글만 옮기고(다른 번호는 건너뜀) 옮긴 수를 돌려준다. 옛 블로그에 글이 없어진 태그는 지운다.
     */
    @Transactional
    public int movePosts(Blog blog, Collection<Long> postIds, Long targetBlogId) {
        if (postIds == null || postIds.isEmpty() || postIds.size() > MAX_POSTS) {
            throw BusinessException.invalidField("postIds", "옮길 글을 1~" + MAX_POSTS + "개 골라 주세요.");
        }
        Blog target = ownTarget(blog, targetBlogId);
        List<Post> posts = postRepository.findAll(PostSpecifications.inBlog(blog.getId())
                .and(PostSpecifications.ownerView())
                .and((root, query, cb) -> root.get("id").in(postIds)));
        if (posts.isEmpty()) {
            return 0;
        }
        Set<Long> oldTagIds = new HashSet<>();
        for (Post post : posts) {
            oldTagIds.addAll(post.tagIds());
            post.replaceTags(tagService.resolve(target, post.tagNames()));
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();
        postRepository.moveTo(ids, target);
        tagService.removeUnused(oldTagIds);
        ids.forEach(id -> events.publishEvent(new PostContentChangedEvent(id)));
        return ids.size();
    }

    /**
     * 이사 대상 정하기. 대상이 이미 다른 곳으로 이사했으면 그 최종 블로그로 정한다(연쇄는 한 번에).
     * 최종 블로그가 이 블로그면 순환이라 400. 이 블로그를 대상으로 두었던 블로그들도 새 최종 블로그로 바꾼다.
     */
    @Transactional
    public void moveTo(Blog blog, Long targetBlogId) {
        Blog target = ownTarget(blog, targetBlogId);
        Blog last = target.isMoved() ? target.getMovedToBlog() : target;
        if (last.getId().equals(blog.getId()) || last.isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_MOVE_TARGET);
        }
        Blog managed = blogRepository.findById(blog.getId()).orElseThrow();
        managed.moveTo(last);
        blogRepository.retarget(blog.getId(), last);
    }

    @Transactional
    public void cancelMove(Blog blog) {
        blogRepository.findById(blog.getId()).orElseThrow().cancelMove();
    }

    /** 같은 회원의 지우지 않은 다른 블로그. 아니면 400 INVALID_MOVE_TARGET(남의 블로그인지 알리지 않으려 404 대신 같은 400). */
    private Blog ownTarget(Blog blog, Long targetBlogId) {
        if (targetBlogId == null) {
            throw BusinessException.invalidField("targetBlogId", "대상 블로그를 골라 주세요.");
        }
        return blogRepository.findWithMemberById(targetBlogId)
                .filter(target -> !target.isDeleted())
                .filter(target -> !target.getId().equals(blog.getId()))
                .filter(target -> target.getMember().getId().equals(blog.getMember().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MOVE_TARGET));
    }

}
