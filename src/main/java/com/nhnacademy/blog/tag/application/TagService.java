package com.nhnacademy.blog.tag.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.tag.domain.Tag;
import com.nhnacademy.blog.tag.domain.TagNames;
import com.nhnacademy.blog.tag.domain.TagRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
     * 이름 목록을 정리(TagNames)하고 블로그 태그로 바꾼다. 이미 있는 태그는 그대로 쓰고(대소문자 무시), 없는 것만 만든다.
     * 글 저장 트랜잭션 안에서 부른다.
     */
    @Transactional
    public List<Tag> resolve(Blog blog, List<String> rawNames) {
        List<String> names = TagNames.normalize(rawNames);
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, Tag> existing = tagRepository.findByBlogIdAndNameIn(blog.getId(), names).stream()
                .collect(Collectors.toMap(tag -> key(tag.getName()), Function.identity(), (first, second) -> first));
        return names.stream()
                .map(name -> existing.computeIfAbsent(key(name), missing -> tagRepository.save(Tag.create(blog, name))))
                .toList();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

}
