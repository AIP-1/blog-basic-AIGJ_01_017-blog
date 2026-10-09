package com.nhnacademy.blog.category.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.category.domain.CategoryRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.post.domain.PostRepository;
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
