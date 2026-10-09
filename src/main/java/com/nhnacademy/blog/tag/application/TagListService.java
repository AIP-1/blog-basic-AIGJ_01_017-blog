package com.nhnacademy.blog.tag.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.tag.domain.TagNames;
import com.nhnacademy.blog.tag.domain.TagRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 태그 목록과 글 수 (T054, TAG-03). 사이드바와 GET /api/tags가 같이 쓴다.
 * 글 수는 블로그 화면의 글 목록과 같은 조건(listedIn)으로 센다. 볼 수 있는 글이 하나도 없는 태그는 빠진다
 * (비공개 글에만 단 태그 이름이 다른 사람에게 보이지 않게, 헌법 원칙 II). 글 수가 많은 순, 같으면 이름순이다.
 */
@Service
public class TagListService {

    private final TagRepository tagRepository;
    private final PostRepository postRepository;
    private final Clock clock;

    public TagListService(TagRepository tagRepository, PostRepository postRepository, Clock clock) {
        this.tagRepository = tagRepository;
        this.postRepository = postRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TagCount> tags(Blog blog, Long viewerId) {
        Map<Long, Long> counts = postRepository.countByTag(
                PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock)));
        Comparator<TagCount> byName = Comparator.comparing(TagCount::name, TagNames.nameComparator());
        return tagRepository.findByBlogId(blog.getId()).stream()
                .filter(tag -> counts.containsKey(tag.getId()))
                .map(tag -> new TagCount(tag.getId(), tag.getName(), counts.get(tag.getId())))
                .sorted(Comparator.comparingLong(TagCount::postCount).reversed().thenComparing(byName))
                .toList();
    }

}
