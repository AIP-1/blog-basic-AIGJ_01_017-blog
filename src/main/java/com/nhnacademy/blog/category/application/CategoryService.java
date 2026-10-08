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
 * 카테고리 추가·이름 변경·삭제 (T029, CAT-01). 주인 검사와 입력 검증은 컨트롤러가 먼저 했다.
 * 하위 카테고리(CAT-03)는 스텝 9에서 더한다. 지금은 모두 최상위다.
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

    /** 맨 아래에 추가한다. 같은 이름이 있으면 409 NAME_TAKEN. */
    @Transactional
    public Category create(Blog blog, String rawName) {
        String name = rawName.trim();
        if (categoryRepository.existsByBlogIdAndParentIsNullAndName(blog.getId(), name)) {
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
        int sortOrder = categoryRepository.findMaxRootSortOrder(blog.getId()) + 1;
        return saveUnique(Category.create(blog, null, name, sortOrder));
    }

    @Transactional
    public void rename(Blog blog, Long categoryId, String rawName) {
        Category category = find(blog, categoryId);
        String name = rawName.trim();
        if (category.getName().equals(name)) {
            return;
        }
        if (!category.getName().equalsIgnoreCase(name)
                && categoryRepository.existsByBlogIdAndParentIsNullAndName(blog.getId(), name)) {
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

    private Category saveUnique(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 같은 이름이 먼저 들어왔다 (UNIQUE blog_id, parent_key, name)
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
    }

}
