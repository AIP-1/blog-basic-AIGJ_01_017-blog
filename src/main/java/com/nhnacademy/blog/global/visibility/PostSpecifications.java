package com.nhnacademy.blog.global.visibility;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberStatus;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.subscription.domain.Subscription;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

/**
 * 글 목록·개수·검색의 가시성 조건 (T011). PostVisibilityPolicy의 ①④를 쿼리 조건으로 바꾼 것이다.
 * 주인이 자기 블로그를 볼 때만 ownerView를 쓰고, 그 밖에는 모두 visibleTo를 쓴다.
 */
public final class PostSpecifications {

    private PostSpecifications() {
    }

    /** 주인이 아닌 사람이 볼 수 있는 글. viewerId가 null이면 비회원이다. */
    public static Specification<Post> visibleTo(Long viewerId, LocalDateTime now) {
        return (root, query, cb) -> {
            Join<Post, Blog> blog = root.join("blog");
            Join<Blog, Member> owner = blog.join("member");

            Predicate ownerNotSuspended = cb.or(
                    cb.notEqual(owner.get("status"), MemberStatus.SUSPENDED),
                    cb.and(cb.isNotNull(owner.get("suspendedUntil")),
                            cb.lessThanOrEqualTo(owner.<LocalDateTime>get("suspendedUntil"), now)));

            Predicate audience = cb.equal(root.get("visibility"), Visibility.PUBLIC);
            if (viewerId != null) {
                Subquery<Long> subscribed = query.subquery(Long.class);
                var subscription = subscribed.from(Subscription.class);
                subscribed.select(subscription.get("id")).where(
                        cb.equal(subscription.get("blog"), blog),
                        cb.equal(subscription.get("member").get("id"), viewerId));
                audience = cb.or(audience, cb.and(
                        cb.equal(root.get("visibility"), Visibility.SUBSCRIBERS), cb.exists(subscribed)));
            }

            return cb.and(
                    cb.isNull(root.get("deletedAt")),
                    cb.isNull(blog.get("deletedAt")),
                    cb.equal(root.get("status"), PostStatus.PUBLISHED),
                    cb.isFalse(root.get("blinded")),
                    cb.isFalse(blog.get("restricted")),
                    ownerNotSuspended,
                    audience);
        };
    }

    /** 블로그 주인이 자기 블로그에서 보는 글: 삭제되지 않은 모든 글. */
    public static Specification<Post> ownerView() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Post> inBlog(Long blogId) {
        return (root, query, cb) -> cb.equal(root.get("blog").get("id"), blogId);
    }

}
