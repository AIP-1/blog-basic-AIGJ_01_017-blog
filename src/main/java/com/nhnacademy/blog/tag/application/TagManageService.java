package com.nhnacademy.blog.tag.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.post.application.PostContentChangedEvent;
import com.nhnacademy.blog.tag.domain.Tag;
import com.nhnacademy.blog.tag.domain.TagNames;
import com.nhnacademy.blog.tag.domain.TagRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 이름 바꾸기·지우기 (T098, TAG-04). 주인 검사는 컨트롤러가 먼저 했다.
 * 같은 블로그에 같은 이름(대소문자·악센트 무시, DB UNIQUE(blog_id, name)와 같은 규칙)은 둘 수 없다. 지워도 글은 남는다.
 */
@Service
public class TagManageService {

    private final TagRepository tagRepository;
    private final ApplicationEventPublisher events;

    public TagManageService(TagRepository tagRepository, ApplicationEventPublisher events) {
        this.tagRepository = tagRepository;
        this.events = events;
    }

    /** 이 블로그의 태그. 없거나 다른 블로그 것이면 404. */
    @Transactional(readOnly = true)
    public Tag find(Blog blog, Long tagId) {
        return tagRepository.findById(tagId)
                .filter(tag -> tag.belongsTo(blog))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 다른 태그와 이름이 겹치면 409 NAME_TAKEN. 자기 이름의 대소문자만 바꾸는 것은 된다. */
    @Transactional
    public void rename(Blog blog, Long tagId, String rawName) {
        Tag tag = find(blog, tagId);
        String name = TagNames.normalizeOne(rawName);
        boolean taken = tagRepository.findByBlogIdAndName(blog.getId(), name)
                .filter(other -> !other.getId().equals(tag.getId()))
                .isPresent();
        if (taken) {
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
        tag.rename(name);
        try {
            tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 같은 이름의 태그가 생겼다(새 글에 단 태그 등)
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
        contentChanged(tagRepository.findPostIdsByTagId(tag.getId()));
    }

    /** 태그를 지운다. 글과의 연결(post_tag)은 외래 키의 ON DELETE CASCADE로 DB가 같이 지우고, 글은 그대로다. */
    @Transactional
    public void delete(Blog blog, Long tagId) {
        Tag tag = find(blog, tagId);
        List<Long> postIds = tagRepository.findPostIdsByTagId(tag.getId());
        tagRepository.delete(tag);
        contentChanged(postIds);
    }

    /**
     * 태그는 추천 임베딩에 넣는 글자의 일부다(R-02). 이름이 바뀌거나 빠진 글은 글을 저장할 때처럼 알린다.
     * 받는 쪽(PostEmbeddingListener)은 커밋된 뒤에 돌므로, 되돌려진 변경으로 임베딩을 만들지 않는다.
     */
    private void contentChanged(List<Long> postIds) {
        postIds.forEach(postId -> events.publishEvent(new PostContentChangedEvent(postId)));
    }

}
