package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.LikePatterns;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.tag.domain.PostTag;
import com.nhnacademy.blog.tag.domain.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 안 검색 (T047, SRCH-01, research R-16). 제목, 본문 글자(content_text), 태그 이름 중 하나에 검색어가 들어간 글.
 * 대소문자는 DB 정렬 규칙(utf8mb4_0900_ai_ci)으로 무시한다. 보는 사람이 볼 수 있는 글만, 최신순 10개씩.
 */
@Service
public class SearchService {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_QUERY_LENGTH = 100;

    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final Clock clock;

    public SearchService(PostRepository postRepository, Clock clock) {
        this.postRepository = postRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Post> search(Blog blog, Long viewerId, String rawQuery, PageQuery page) {
        String query = normalize(rawQuery);
        String pattern = LikePatterns.contains(query);
        Specification<Post> matches = (root, criteria, cb) -> {
            // 태그 이름은 글마다 여럿이라, 조인하면 같은 글이 여러 번 나온다. 그래서 EXISTS 부분 쿼리로 본다
            Subquery<Long> tagged = criteria.subquery(Long.class);
            Root<Post> sameRow = tagged.correlate(root);
            Join<PostTag, Tag> tag = sameRow.join("postTags").join("tag");
            tagged.select(tag.get("id")).where(cb.like(tag.get("name"), pattern, LikePatterns.ESCAPE));
            return cb.or(
                    cb.like(root.get("title"), pattern, LikePatterns.ESCAPE),
                    cb.like(root.get("contentText"), pattern, LikePatterns.ESCAPE),
                    cb.exists(tagged));
        };
        return postRepository.findAll(
                PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock)).and(matches),
                page.toPageable(LATEST));
    }

    /** 앞뒤 공백을 떼고, 비었거나 공백뿐이면 400, 너무 길면 400. */
    static String normalize(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            throw BusinessException.invalidField("q", "검색어를 입력해 주세요.");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw BusinessException.invalidField("q", "검색어는 " + MAX_QUERY_LENGTH + "자까지입니다.");
        }
        return query;
    }

}
