package com.nhnacademy.blog.global.visibility;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberStatus;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.subscription.domain.Subscription;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

/**
 * 글 목록·개수·검색의 가시성 조건 (T011). PostVisibilityPolicy의 ①④를 쿼리 조건으로 바꾼 것이다.
 * 주인이 자기 블로그를 볼 때만 ownerView를 쓰고, 그 밖에는 모두 visibleTo를 쓴다.
 * 블로그 화면의 글 목록(메인, 사이드바)은 둘을 고르는 listedIn을 쓴다.
 */
public final class PostSpecifications {

    private PostSpecifications() {
    }

    /** 주인이 아닌 사람이 볼 수 있는 글. viewerId가 null이면 비회원이다. */
    public static Specification<Post> visibleTo(Long viewerId, LocalDateTime now) {
        return (root, query, cb) -> visibleTo(root, query, cb, viewerId, now);
    }

    /** 블로그 주인이 자기 블로그에서 보는 글: 삭제되지 않은 모든 글. */
    public static Specification<Post> ownerView() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /**
     * 블로그 화면(메인 글 목록, 사이드바 최근 글·글 수)에 나오는 글 (BLOG-03, BLOG-04).
     * 주인에게는 비공개·숨긴 글을 포함한 발행 글 전부, 다른 사람에게는 볼 수 있는 글만이다.
     * 임시저장·예약 글은 주인에게도 블로그 화면에 나오지 않는다(관리 화면의 글 관리에서 본다).
     */
    public static Specification<Post> listedIn(Blog blog, Long viewerId, LocalDateTime now) {
        return (root, query, cb) -> listedIn(root, query, cb, blog, viewerId, now);
    }

    /** listedIn을 다른 엔티티의 조인(댓글 → 글 등)에 쓸 때. post는 Root이거나 Join이다. */
    public static Predicate listedIn(From<?, Post> post, CriteriaQuery<?> query, CriteriaBuilder cb, Blog blog,
                                     Long viewerId, LocalDateTime now) {
        Predicate inBlog = cb.equal(post.get("blog").get("id"), blog.getId());
        if (blog.isOwnedBy(viewerId)) {
            return cb.and(inBlog, cb.isNull(post.get("deletedAt")),
                    cb.equal(post.get("status"), PostStatus.PUBLISHED));
        }
        return cb.and(inBlog, visibleTo(post, query, cb, viewerId, now));
    }

    public static Specification<Post> inBlog(Long blogId) {
        return (root, query, cb) -> cb.equal(root.get("blog").get("id"), blogId);
    }

    private static Predicate visibleTo(From<?, Post> post, CriteriaQuery<?> query, CriteriaBuilder cb,
                                       Long viewerId, LocalDateTime now) {
        Join<Post, Blog> blog = post.join("blog");
        Join<Blog, Member> owner = blog.join("member");

        Predicate ownerNotSuspended = cb.or(
                cb.notEqual(owner.get("status"), MemberStatus.SUSPENDED),
                cb.and(cb.isNotNull(owner.get("suspendedUntil")),
                        cb.lessThanOrEqualTo(owner.<LocalDateTime>get("suspendedUntil"), now)));

        Predicate audience = cb.equal(post.get("visibility"), Visibility.PUBLIC);
        if (viewerId != null) {
            Subquery<Long> subscribed = query.subquery(Long.class);
            var subscription = subscribed.from(Subscription.class);
            subscribed.select(subscription.get("id")).where(
                    cb.equal(subscription.get("blog"), blog),
                    cb.equal(subscription.get("member").get("id"), viewerId));
            audience = cb.or(audience, cb.and(
                    cb.equal(post.get("visibility"), Visibility.SUBSCRIBERS), cb.exists(subscribed)));
        }

        // 비공개 카테고리(CAT-05): 글의 카테고리나 그 상위가 비공개면 주인 말고는 없는 글이다.
        // 조인 대신 부분 쿼리로 본다(카테고리별 글 수처럼 카테고리로 묶는 쿼리와 조인이 엉키지 않게)
        Subquery<Long> hiddenCategory = query.subquery(Long.class);
        Root<Category> category = hiddenCategory.from(Category.class);
        Join<Category, Category> parent = category.join("parent", JoinType.LEFT);
        hiddenCategory.select(category.get("id")).where(
                cb.equal(category.get("id"), post.get("category").get("id")),
                cb.or(cb.isTrue(category.get("privateCategory")), cb.isTrue(parent.get("privateCategory"))));

        return cb.and(
                cb.isNull(post.get("deletedAt")),
                cb.isNull(blog.get("deletedAt")),
                cb.not(cb.exists(hiddenCategory)),
                cb.equal(post.get("status"), PostStatus.PUBLISHED),
                cb.isFalse(post.get("blinded")),
                cb.isFalse(blog.get("restricted")),
                ownerNotSuspended,
                audience);
    }

}
