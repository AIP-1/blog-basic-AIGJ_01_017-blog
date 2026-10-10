package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.global.web.LikePatterns;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberStatus;
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
 * 검색 (research R-16). 제목, 본문 글자(content_text), 태그 이름 중 하나에 검색어가 들어간 글.
 * 대소문자는 DB 정렬 규칙(utf8mb4_0900_ai_ci)으로 무시한다. 보는 사람이 볼 수 있는 글만, 최신순 10개씩.
 * <ul>
 *   <li>블로그 안 검색 (T047, SRCH-01): 그 블로그의 글. 주인에게는 자기 비공개 글도(블로그 화면과 같은 listedIn)</li>
 *   <li>전체 검색 (T065, SRCH-02): 모든 블로그의 글(홈 최신 글과 같은 visibleTo), 또는 이름·소개로 찾은 블로그</li>
 * </ul>
 */
@Service
public class SearchService {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_QUERY_LENGTH = 100;

    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private static final Sort NEWEST_BLOG = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final BlogRepository blogRepository;
    private final Clock clock;

    public SearchService(PostRepository postRepository, BlogRepository blogRepository, Clock clock) {
        this.postRepository = postRepository;
        this.blogRepository = blogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Post> search(Blog blog, Long viewerId, String rawQuery, PageQuery page) {
        return postRepository.findAll(
                PostSpecifications.listedIn(blog, viewerId, LocalDateTime.now(clock)).and(matches(rawQuery)),
                page.toPageable(LATEST));
    }

    /**
     * 전체 글 검색 (SRCH-02). 홈 최신 글처럼 남에게 보이는 글만 찾는다(비공개·구독 안 한 구독자 공개·숨긴 글·
     * 이용 제한 블로그·정지 회원 블로그 제외). 자기 비공개 글은 자기 블로그 안 검색에서 찾는다.
     * 목록 한 줄에 블로그 이름과 카테고리가 나가므로 함께 읽는다.
     */
    @Transactional(readOnly = true)
    public Page<Post> searchAll(Long viewerId, String rawQuery, PageQuery page) {
        Specification<Post> condition = PostSpecifications.visibleTo(viewerId, LocalDateTime.now(clock))
                .and(matches(rawQuery));
        return postRepository.findBy(condition, query -> query.project("blog", "category")
                .page(page.toPageable(LATEST)));
    }

    /**
     * 블로그 검색 (SRCH-02). 이름이나 소개에 검색어가 들어간, 남에게 보이는 블로그(지우지 않음, 이용 제한 아님,
     * 주인이 정지 중이 아님)를 새로 만든 순으로. 이사한 블로그는 새 블로그로 이어지므로 뺀다(같은 블로그가 두 번 나오지 않게).
     */
    @Transactional(readOnly = true)
    public Page<Blog> searchBlogs(String rawQuery, PageQuery page) {
        String pattern = LikePatterns.contains(normalize(rawQuery));
        LocalDateTime now = LocalDateTime.now(clock);
        Specification<Blog> condition = (root, query, cb) -> {
            Join<Blog, Member> owner = root.join("member");
            return cb.and(
                    cb.isNull(root.get("deletedAt")),
                    cb.isFalse(root.get("restricted")),
                    cb.isNull(root.get("movedToBlog")),
                    cb.or(cb.notEqual(owner.get("status"), MemberStatus.SUSPENDED),
                            cb.and(cb.isNotNull(owner.get("suspendedUntil")),
                                    cb.lessThanOrEqualTo(owner.<LocalDateTime>get("suspendedUntil"), now))),
                    cb.or(cb.like(root.get("name"), pattern, LikePatterns.ESCAPE),
                            cb.like(root.get("description"), pattern, LikePatterns.ESCAPE)));
        };
        return blogRepository.findBy(condition, query -> query.project("member")
                .page(page.toPageable(NEWEST_BLOG)));
    }

    /** 제목·본문 글자·태그 이름 중 하나에 검색어가 들어간 글. */
    private static Specification<Post> matches(String rawQuery) {
        String query = normalize(rawQuery);
        String pattern = LikePatterns.contains(query);
        return (root, criteria, cb) -> {
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
