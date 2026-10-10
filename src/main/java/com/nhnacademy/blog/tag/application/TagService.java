package com.nhnacademy.blog.tag.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.tag.domain.Tag;
import com.nhnacademy.blog.tag.domain.TagNames;
import com.nhnacademy.blog.tag.domain.TagRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글에 달 태그 찾기·만들기 (T038, TAG-01). 블로그에 없는 이름은 새 태그가 된다.
 */
@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * 이름 목록을 정리(TagNames)하고 블로그 태그로 바꾼다. 이미 있는 태그는 그대로 쓰고(대소문자·악센트 무시), 없는 것만 만든다.
     * 글 저장 트랜잭션 안에서 부른다.
     */
    @Transactional
    public List<Tag> resolve(Blog blog, List<String> rawNames) {
        List<String> names = TagNames.normalize(rawNames);
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, Tag> existing = new TreeMap<>(TagNames.nameComparator());
        List<Long> found = tagRepository.findByBlogIdAndNameIn(blog.getId(), names).stream().map(Tag::getId).toList();
        lockExisting(found).forEach(tag -> existing.putIfAbsent(tag.getName(), tag));
        return names.stream()
                .map(name -> existing.computeIfAbsent(name, missing -> create(blog, missing)))
                .distinct()
                .toList();
    }

    /**
     * 글을 지우거나 글의 태그를 바꾼 뒤 부른다. 주어진 태그 가운데 지우지 않은 글이 하나도 없는 태그를 지운다
     * (2026-10-11 지원 결정). 그래서 관리 화면의 태그 목록, 태그 주소, 같은 이름 검사에 "글 없는 태그"가 남지 않는다.
     */
    @Transactional
    public void removeUnused(Collection<Long> tagIds) {
        if (!tagIds.isEmpty()) {
            tagRepository.deleteUnused(tagIds);
        }
    }

    private List<Tag> lockExisting(List<Long> ids) {
        return ids.isEmpty() ? List.of() : tagRepository.findLockedByIdIn(ids);
    }

    /** 없으면 넣고, 넣었든 다른 트랜잭션이 먼저 넣었든 DB의 그 행을 읽는다. 같은 이름 판단은 DB가 한다. */
    private Tag create(Blog blog, String name) {
        tagRepository.insertIfAbsent(blog.getId(), name);
        return tagRepository.findLockedByBlogIdAndName(blog.getId(), name).orElseThrow();
    }

}
