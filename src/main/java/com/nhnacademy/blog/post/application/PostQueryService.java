package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.tag.domain.Tag;
import com.nhnacademy.blog.tag.domain.TagRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 메인 글 목록 (T024, BLOG-03, CAT-02). 최신순(처음 발행 시각 내림차순, 같으면 나중 글이 위), 10개씩.
 * 보는 사람이 볼 수 있는 글만 나온다(PostSpecifications.listedIn).
 */
@Service
public class PostQueryService {

    public static final int BLOG_PAGE_SIZE = 10;
    /** 미분류를 고르는 categoryId (contracts/rest-api.md POST). */
    public static final long UNCATEGORIZED = 0;

    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final Clock clock;

    public PostQueryService(PostRepository postRepository, CategoryRepository categoryRepository,
                            TagRepository tagRepository, Clock clock) {
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.clock = clock;
    }

    /**
     * categoryId: null이면 전체, 0이면 미분류, 그 밖에는 그 카테고리와 하위 카테고리의 글.
     * tag: 그 이름의 태그가 달린 글(TAG-02, 대소문자 무시). 블로그에 없는 태그면 404.
     */
    @Transactional(readOnly = true)
    public Page<Post> blogPosts(Blog blog, Long viewerId, Long categoryId, String tag, PageQuery page) {
        Specification<Post> condition = PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock));
        if (categoryId != null) {
            condition = condition.and(inCategory(blog, viewerId, categoryId));
        }
        if (tag != null) {
            condition = condition.and(taggedWith(blog, tag));
        }
        return postRepository.findAll(condition, page.toPageable(LATEST));
    }

    private Specification<Post> taggedWith(Blog blog, String name) {
        Tag tag = tagRepository.findByBlogIdAndName(blog.getId(), name.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return (root, query, cb) -> cb.equal(root.join("postTags").get("tag").get("id"), tag.getId());
    }

    /** 비공개 카테고리와, 비공개 상위 아래의 하위 카테고리는 주인이 아니면 없는 것과 같다(사이드바 트리와 같은 규칙). */
    private static boolean hiddenCategory(Category category) {
        return category.isPrivateCategory() || (category.isChild() && category.getParent().isPrivateCategory());
    }

    private Specification<Post> inCategory(Blog blog, Long viewerId, long categoryId) {
        if (categoryId == UNCATEGORIZED) {
            return (root, query, cb) -> cb.isNull(root.get("category"));
        }
        // 다른 블로그의 카테고리, 주인이 아닌 사람에게 비공개 카테고리는 없는 것과 같다
        Category category = categoryRepository.findById(categoryId)
                .filter(found -> found.getBlog().getId().equals(blog.getId()))
                .filter(found -> blog.isOwnedBy(viewerId) || !hiddenCategory(found))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return (root, query, cb) -> {
            var postCategory = root.join("category", JoinType.LEFT);
            return cb.or(cb.equal(postCategory.get("id"), category.getId()),
                    cb.equal(postCategory.get("parent").get("id"), category.getId()));
        };
    }

}
