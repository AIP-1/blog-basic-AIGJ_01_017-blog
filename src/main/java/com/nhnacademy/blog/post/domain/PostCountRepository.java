package com.nhnacademy.blog.post.domain;

import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/**
 * Spring Data가 이름으로 못 만드는 집계 쿼리. 구현은 PostCountRepositoryImpl(이름 규칙으로 연결된다).
 */
public interface PostCountRepository {

    /** 조건에 맞는 글 수를 카테고리별로. 키 null은 미분류다. */
    Map<Long, Long> countByCategory(Specification<Post> condition);

}
