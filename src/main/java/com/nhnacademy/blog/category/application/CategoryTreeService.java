package com.nhnacademy.blog.category.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 트리와 글 수 (T025, CAT-02, BLOG-04). 글 수는 보는 사람이 볼 수 있는 글만 센다.
 * 쿼리는 두 번이다: 카테고리 전부, 카테고리별 글 수(group by). 트리는 메모리에서 짠다.
 * 하위 카테고리(CAT-03)가 생기기 전에는 모두 최상위라 한 단계로 보인다.
 */
@Service
public class CategoryTreeService {

    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final Clock clock;

    public CategoryTreeService(CategoryRepository categoryRepository, PostRepository postRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.postRepository = postRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CategoryTree tree(Blog blog, Long viewerId) {
        boolean owner = blog.isOwnedBy(viewerId);
        Map<Long, Long> counts = postRepository.countByCategory(
                PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock)));

        // 주인이 아니면 비공개 카테고리(CAT-05)는 없는 것처럼, 그 아래 카테고리도 함께 뺀다
        List<Category> categories = categoryRepository.findByBlogIdOrderBySortOrderAscIdAsc(blog.getId()).stream()
                .filter(category -> owner || !category.isPrivateCategory())
                .toList();
        Map<Long, List<CategoryTree.Node>> childrenByParent = new LinkedHashMap<>();
        for (Category category : categories) {
            if (category.getParent() != null) {
                childrenByParent.computeIfAbsent(category.getParent().getId(), key -> new ArrayList<>())
                        .add(leaf(category, counts));
            }
        }
        List<CategoryTree.Node> roots = categories.stream()
                .filter(category -> category.getParent() == null)
                .map(category -> withChildren(category, counts,
                        childrenByParent.getOrDefault(category.getId(), List.of())))
                .toList();

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new CategoryTree(total, counts.getOrDefault(null, 0L), roots, owner);
    }

    private CategoryTree.Node leaf(Category category, Map<Long, Long> counts) {
        return withChildren(category, counts, List.of());
    }

    private CategoryTree.Node withChildren(Category category, Map<Long, Long> counts,
                                           List<CategoryTree.Node> children) {
        long count = counts.getOrDefault(category.getId(), 0L)
                + children.stream().mapToLong(CategoryTree.Node::postCount).sum();
        return new CategoryTree.Node(category.getId(), category.getName(), category.isPrivateCategory(), count,
                category.getSortOrder(), children);
    }

}
