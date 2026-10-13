package com.nhnacademy.blog.post.domain;

import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/**
 * Spring Data가 이름으로 못 만드는 집계 쿼리. 구현은 PostCountRepositoryImpl(이름 규칙으로 연결된다).
 */
public interface PostCountRepository {

    /** 조건에 맞는 글 수를 카테고리별로. 키 null은 미분류다. */
    Map<Long, Long> countByCategory(Specification<Post> condition);

    /** 조건에 맞는 글 수를 태그 id별로. 글이 하나도 없는 태그는 결과에 없다. */
    Map<Long, Long> countByTag(Specification<Post> condition);

    /** 조건에 맞는 글 번호만. 글을 통째로 읽지 않는다(블로그 안 의미 검색의 후보 범위, T121). */
    List<Long> findIds(Specification<Post> condition);

}
