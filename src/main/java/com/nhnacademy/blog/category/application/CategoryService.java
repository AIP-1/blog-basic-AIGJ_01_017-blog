package com.nhnacademy.blog.category.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 추가·이름 변경·삭제 (T029, CAT-01)와 하위 카테고리 (T060, CAT-03). 주인 검사와 입력 검증은 컨트롤러가 먼저 했다.
 * 카테고리는 2단계까지다. 이름은 같은 자리(최상위끼리, 같은 상위의 하위끼리)에서만 겹치면 안 된다.
 * 상위와 하위는 같은 이름이어도 된다(DB UNIQUE blog_id, parent_key, name과 같은 규칙).
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;

    public CategoryService(CategoryRepository categoryRepository, PostRepository postRepository) {
        this.categoryRepository = categoryRepository;
        this.postRepository = postRepository;
    }

    /** 이 블로그의 카테고리. 없거나 다른 블로그 것이면 404. */
    @Transactional(readOnly = true)
    public Category find(Blog blog, Long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(category -> category.belongsTo(blog))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * 그 자리의 맨 아래에 추가한다. parentId가 있으면 그 카테고리의 하위로.
     * 상위가 이 블로그에 없으면 400(parentId), 상위가 이미 하위 카테고리면 409 CATEGORY_DEPTH(3단계가 됨),
     * 같은 자리에 같은 이름이 있으면 409 NAME_TAKEN.
     */
    @Transactional
    public Category create(Blog blog, String rawName, Long parentId) {
        String name = rawName.trim();
        Category parent = parent(blog, parentId);
        if (nameTaken(blog, parent, name)) {
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
        int sortOrder = parent == null
                ? categoryRepository.findMaxRootSortOrder(blog.getId()) + 1
                : categoryRepository.findMaxChildSortOrder(parent.getId()) + 1;
        return saveUnique(Category.create(blog, parent, name, sortOrder));
    }

    @Transactional
    public void rename(Blog blog, Long categoryId, String rawName) {
        Category category = find(blog, categoryId);
        String name = rawName.trim();
        if (category.getName().equals(name)) {
            return;
        }
        if (!category.getName().equalsIgnoreCase(name) && nameTaken(blog, category.getParent(), name)) {
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
        category.rename(name);
        saveUnique(category);
    }

    /**
     * 비공개 켜기·끄기 (CAT-05). 비공개 카테고리와 그 하위의 글은 주인 말고는 볼 수 없다(목록·검색·홈·글 상세·글 수).
     * 하위 카테고리를 따로 비공개로 해도 된다. 상위가 비공개면 하위는 자기 설정과 상관없이 숨는다.
     */
    @Transactional
    public void changePrivate(Blog blog, Long categoryId, boolean privateCategory) {
        find(blog, categoryId).changePrivate(privateCategory);
    }

    /**
     * 순서·상하위 한 번에 바꾸기 (CAT-04, 끌어서 놓은 결과). 이 블로그의 카테고리 전부를 한 번씩 보내야 한다.
     * <ul>
     *   <li>빠졌거나, 두 번 나왔거나, 남의·없는 번호가 있으면 400(order)</li>
     *   <li>자기 자신이나 하위 카테고리를 상위로 두면(3단계가 됨) 409 CATEGORY_DEPTH</li>
     *   <li>같은 자리에 같은 이름(대소문자 무시)이 둘이면 409 NAME_TAKEN</li>
     * </ul>
     * 정렬은 받은 sortOrder 순(같으면 보낸 순서)으로 자리마다 0, 1, 2…로 다시 매긴다.
     */
    @Transactional
    public void reorder(Blog blog, List<OrderItem> items) {
        Map<Long, Category> mine = categoryRepository.findByBlogIdOrderBySortOrderAscIdAsc(blog.getId()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Set<Long> sent = new HashSet<>();
        for (OrderItem item : items) {
            if (item.id() == null || !mine.containsKey(item.id()) || !sent.add(item.id())) {
                throw BusinessException.invalidField("order", "카테고리 목록이 맞지 않습니다. 새로 고친 뒤 다시 해 주세요.");
            }
        }
        if (sent.size() != mine.size()) {
            throw BusinessException.invalidField("order", "카테고리 목록이 맞지 않습니다. 새로 고친 뒤 다시 해 주세요.");
        }
        Map<Long, Long> parentOf = new HashMap<>();
        items.forEach(item -> parentOf.put(item.id(), item.parentId()));
        for (OrderItem item : items) {
            Long parentId = item.parentId();
            if (parentId == null) {
                continue;
            }
            // 상위는 이 블로그의 최상위 카테고리여야 한다(자기 자신, 하위의 하위 = 3단계 금지)
            if (!mine.containsKey(parentId) || parentId.equals(item.id()) || parentOf.get(parentId) != null) {
                throw new BusinessException(ErrorCode.CATEGORY_DEPTH);
            }
        }
        Map<Long, List<OrderItem>> byParent = items.stream().collect(Collectors.groupingBy(
                item -> item.parentId() == null ? 0L : item.parentId(), LinkedHashMap::new, Collectors.toList()));
        for (List<OrderItem> siblings : byParent.values()) {
            Set<String> names = new HashSet<>();
            for (OrderItem item : siblings) {
                if (!names.add(mine.get(item.id()).getName().toLowerCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.NAME_TAKEN);
                }
            }
            List<OrderItem> sorted = siblings.stream()
                    .sorted(Comparator.comparingInt(item -> item.sortOrder() == null ? Integer.MAX_VALUE : item.sortOrder()))
                    .toList();
            for (int i = 0; i < sorted.size(); i++) {
                OrderItem item = sorted.get(i);
                mine.get(item.id()).moveTo(item.parentId() == null ? null : mine.get(item.parentId()), i);
            }
        }
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 바꾸는 중간에 같은 자리 같은 이름이 잠깐 겹쳤다(두 카테고리의 상위를 서로 맞바꿈 등)
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
    }

    /** 순서 바꾸기 한 줄. 표현 계층의 요청을 서비스가 직접 받지 않으려고 따로 둔다. */
    public record OrderItem(Long id, Long parentId, Integer sortOrder) {
    }

    /** 지우면 소속 글은 미분류가 된다. 하위 카테고리가 있으면 409 CATEGORY_HAS_CHILDREN. */
    @Transactional
    public void delete(Blog blog, Long categoryId) {
        Category category = find(blog, categoryId);
        if (categoryRepository.existsByParentId(category.getId())) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        postRepository.uncategorize(category.getId());
        categoryRepository.deleteById(category.getId());
    }

    private Category parent(Blog blog, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Category parent = categoryRepository.findById(parentId)
                .filter(category -> category.belongsTo(blog))
                .orElseThrow(() -> BusinessException.invalidField("parentId", "상위 카테고리를 찾을 수 없습니다."));
        if (parent.isChild()) {
            throw new BusinessException(ErrorCode.CATEGORY_DEPTH);
        }
        return parent;
    }

    /** 같은 자리(parent가 null이면 최상위)에 같은 이름이 있는가. 대소문자는 DB 정렬 규칙대로 같게 본다. */
    private boolean nameTaken(Blog blog, Category parent, String name) {
        return parent == null
                ? categoryRepository.existsByBlogIdAndParentIsNullAndName(blog.getId(), name)
                : categoryRepository.existsByParentIdAndName(parent.getId(), name);
    }

    private Category saveUnique(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 같은 이름이 먼저 들어왔다 (UNIQUE blog_id, parent_key, name)
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
    }

}
