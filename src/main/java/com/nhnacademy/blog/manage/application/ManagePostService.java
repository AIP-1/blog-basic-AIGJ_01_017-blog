package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.LikePatterns;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.post.application.PostService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.Visibility;
import jakarta.persistence.criteria.JoinType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 글 관리 (T067, MNG-01). 블로그 주인이 자기 블로그의 지우지 않은 글을 모두 본다(비공개·숨긴 글, 임시저장·예약 포함).
 * 주인 검사는 컨트롤러가 먼저 했다(BlogOwnerGuard).
 * 일괄 공개 범위 변경·삭제는 글 하나씩 바꾸는 것(POST-06, POST-03)과 같은 규칙을 여러 글에 한 트랜잭션으로 적용한다.
 */
@Service
public class ManagePostService {

    public static final int PAGE_SIZE = 20;
    /** 미분류를 고르는 categoryId (블로그 글 목록과 같음). */
    public static final long UNCATEGORIZED = 0L;

    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final PostService postService;

    public ManagePostService(PostRepository postRepository, CategoryRepository categoryRepository,
                             PostService postService) {
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.postService = postService;
    }

    /**
     * 최신순 20개씩. 발행 글은 처음 발행 시각, 아직 발행하지 않은 글(임시저장·예약)은 수정 시각으로 줄 세운다
     * (contracts MNG-01 "임시저장은 수정 시각순"). 두 시각을 한 줄로 섞으려고 COALESCE(published_at, updated_at)로 정렬한다.
     */
    @Transactional(readOnly = true)
    public ManagedPostPage posts(Blog blog, ManagePostFilter filter, PageQuery page) {
        Specification<Post> condition = mine(blog).and(newestFirst());
        if (filter.status() != null) {
            condition = condition.and((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.visibility() != null) {
            condition = condition.and((root, query, cb) -> cb.equal(root.get("visibility"), filter.visibility()));
        }
        if (filter.categoryId() != null) {
            condition = condition.and(inCategory(blog, filter.categoryId()));
        }
        if (filter.q() != null) {
            String pattern = LikePatterns.contains(filter.q());
            condition = condition.and((root, query, cb) -> cb.like(root.get("title"), pattern, LikePatterns.ESCAPE));
        }
        // 정렬은 newestFirst가 쿼리에 직접 건다. 페이지 요청에는 정렬을 넣지 않는다(넣으면 그것으로 덮인다)
        Page<Post> posts = postRepository.findAll(condition, page.toPageable(Sort.unsorted()));
        Map<Long, Map<String, String>> blindReasons = new LinkedHashMap<>();
        posts.getContent().stream()
                .filter(Post::isBlinded)
                .forEach(post -> blindReasons.put(post.getId(), postService.blindReason(post)));
        return new ManagedPostPage(posts, blindReasons);
    }

    /**
     * 고른 글들의 공개 범위를 바꾼다. 이 블로그의 지우지 않은 글만 바꾸고, 그 수를 돌려준다
     * (다른 블로그 글·지운 글·없는 번호는 조용히 건너뛴다). 숨긴 글도 공개 범위는 바꿀 수 있다(글 하나와 같음).
     */
    @Transactional
    public int changeVisibility(Blog blog, Collection<Long> postIds, Visibility visibility) {
        List<Post> posts = postRepository.findAll(mine(blog).and(idIn(postIds)));
        posts.forEach(post -> post.changeVisibility(visibility));
        return posts.size();
    }

    /**
     * 고른 글들을 지운다. 글 하나 삭제(PostService.delete)와 같이 댓글은 소프트 삭제, 공감·알림은 지운다.
     * 하나라도 실패하면 모두 되돌린다(한 트랜잭션). 지운 수를 돌려준다.
     */
    @Transactional
    public int delete(Blog blog, Collection<Long> postIds, LoginMember member) {
        List<Long> ids = postRepository.findAll(mine(blog).and(idIn(postIds))).stream().map(Post::getId).toList();
        ids.forEach(id -> postService.delete(blog, id, member));
        return ids.size();
    }

    /** 이 블로그의 지우지 않은 글 전부 (주인이 보는 글). */
    private static Specification<Post> mine(Blog blog) {
        return PostSpecifications.inBlog(blog.getId()).and(PostSpecifications.ownerView());
    }

    private static Specification<Post> idIn(Collection<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    /** 발행 시각(없으면 수정 시각) 최신순, 같으면 id 큰 순. 개수 쿼리에는 걸지 않는다. */
    private static Specification<Post> newestFirst() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(cb.coalesce(root.get("publishedAt"), root.get("updatedAt"))),
                        cb.desc(root.get("id")));
            }
            return null;
        };
    }

    /** 0이면 미분류, 그 밖에는 그 카테고리와 하위 카테고리. 이 블로그 카테고리가 아니면 404. */
    private Specification<Post> inCategory(Blog blog, long categoryId) {
        if (categoryId == UNCATEGORIZED) {
            return (root, query, cb) -> cb.isNull(root.get("category"));
        }
        Category category = categoryRepository.findById(categoryId)
                .filter(found -> found.belongsTo(blog))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return (root, query, cb) -> {
            var postCategory = root.join("category", JoinType.LEFT);
            return cb.or(cb.equal(postCategory.get("id"), category.getId()),
                    cb.equal(postCategory.get("parent").get("id"), category.getId()));
        };
    }

}
