package com.nhnacademy.blog.tag.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** 이름이 들어 있는 블로그 태그. 비교는 DB 정렬 규칙대로 대소문자를 구분하지 않는다. */
    List<Tag> findByBlogIdAndNameIn(Long blogId, Collection<String> names);

    Optional<Tag> findByBlogIdAndName(Long blogId, String name);

}
