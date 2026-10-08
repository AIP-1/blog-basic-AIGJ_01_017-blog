package com.nhnacademy.blog.global.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.subscription.domain.Subscription;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 가시성 판단 (T011). data-model.md 표의 결과 6개와 목록 조건을 확인한다.
 */
@Transactional
class PostVisibilityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    PostVisibilityPolicy policy;

    @Autowired
    PostRepository postRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    Member owner;
    Member other;
    Blog blog;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        other = testMembers.create();
        blog = testBlogs.create(owner);
    }

    @Test
    void publicPostIsVisibleToEveryone() {
        Post post = published(Visibility.PUBLIC);

        assertThat(policy.decide(post.getId(), blog, null)).isInstanceOf(PostAccess.Visible.class);
        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.Visible.class);
        assertThat(policy.decide(post.getId(), null, null)).isInstanceOf(PostAccess.Visible.class);
    }

    @Test
    void privatePostIs404ToOthersEvenWhenLoggedIn() {
        Post post = published(Visibility.PRIVATE);

        assertThat(policy.decide(post.getId(), blog, null)).isInstanceOf(PostAccess.NotFound.class);
        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.NotFound.class);
        assertThat(policy.decide(post.getId(), blog, owner.getId())).isInstanceOf(PostAccess.Owner.class);
    }

    @Test
    void draftIsOwnerOnly() {
        Post post = postRepository.save(Post.draft(blog, null, "임시", "<p>x</p>", "x", Visibility.PUBLIC, null));

        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.NotFound.class);
        assertThat(policy.decide(post.getId(), blog, owner.getId())).isInstanceOf(PostAccess.Owner.class);
    }

    @Test
    void deletedPostOrBlogIs404EvenForOwner() {
        Post deletedPost = published(Visibility.PUBLIC);
        Post postInDeletedBlog = published(Visibility.PUBLIC);
        sql("UPDATE post SET deleted_at = NOW() WHERE id = ?", deletedPost.getId());

        assertThat(policy.decide(deletedPost.getId(), blog, owner.getId())).isInstanceOf(PostAccess.NotFound.class);

        sql("UPDATE blog SET deleted_at = NOW() WHERE id = ?", blog.getId());
        assertThat(policy.decide(postInDeletedBlog.getId(), null, owner.getId()))
                .isInstanceOf(PostAccess.NotFound.class);
        assertThat(policy.decide(-1L, null, owner.getId())).isInstanceOf(PostAccess.NotFound.class);
    }

    @Test
    void blindedPostIsShownOnlyToOwnerWithFlag() {
        Post post = published(Visibility.PUBLIC);
        sql("UPDATE post SET is_blinded = 1 WHERE id = ?", post.getId());

        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.NotFound.class);
        assertThat(policy.decide(post.getId(), blog, owner.getId()))
                .isInstanceOfSatisfying(PostAccess.Owner.class, access -> assertThat(access.blinded()).isTrue());
    }

    @Test
    void subscribersOnlyPostShowsNoticeUntilSubscribed() {
        Post post = published(Visibility.SUBSCRIBERS);

        assertThat(policy.decide(post.getId(), blog, null)).isInstanceOf(PostAccess.SubscribersOnly.class);
        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.SubscribersOnly.class);

        subscriptionRepository.save(Subscription.subscribe(other, blog));
        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.Visible.class);
    }

    @Test
    void restrictedBlogOrSuspendedOwnerHidesPosts() {
        Post post = published(Visibility.PUBLIC);
        sql("UPDATE blog SET is_restricted = 1 WHERE id = ?", blog.getId());

        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.NotFound.class);
        assertThat(policy.decide(post.getId(), blog, owner.getId())).isInstanceOf(PostAccess.Owner.class);

        sql("UPDATE blog SET is_restricted = 0 WHERE id = ?", blog.getId());
        sql("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", owner.getId());
        assertThat(policy.decide(post.getId(), blog, other.getId())).isInstanceOf(PostAccess.NotFound.class);
    }

    @Test
    void postOfAnotherBlogRedirectsOnlyWhenVisible() {
        Blog otherBlog = testBlogs.create(other);
        Post publicPost = published(Visibility.PUBLIC);
        Post privatePost = published(Visibility.PRIVATE);

        assertThat(policy.decide(publicPost.getId(), otherBlog, null))
                .isInstanceOfSatisfying(PostAccess.MovedTo.class,
                        access -> assertThat(access.blog().getId()).isEqualTo(blog.getId()));
        assertThat(policy.decide(privatePost.getId(), otherBlog, null)).isInstanceOf(PostAccess.NotFound.class);
        assertThat(policy.decide(privatePost.getId(), otherBlog, owner.getId()))
                .isInstanceOf(PostAccess.MovedTo.class);
    }

    @Test
    void listConditionMatchesDetailDecision() {
        Post publicPost = published(Visibility.PUBLIC);
        Post subscribersPost = published(Visibility.SUBSCRIBERS);
        Post privatePost = published(Visibility.PRIVATE);
        Post draft = postRepository.save(Post.draft(blog, null, "임시", "<p>x</p>", "x", Visibility.PUBLIC, null));
        Post blinded = published(Visibility.PUBLIC);
        Post deleted = published(Visibility.PUBLIC);
        sql("UPDATE post SET is_blinded = 1 WHERE id = ?", blinded.getId());
        sql("UPDATE post SET deleted_at = NOW() WHERE id = ?", deleted.getId());
        subscriptionRepository.save(Subscription.subscribe(other, blog));

        LocalDateTime now = LocalDateTime.now();
        assertThat(ids(PostSpecifications.visibleTo(null, now))).containsExactly(publicPost.getId());
        assertThat(ids(PostSpecifications.visibleTo(other.getId(), now)))
                .containsExactlyInAnyOrder(publicPost.getId(), subscribersPost.getId());
        assertThat(ids(PostSpecifications.ownerView()))
                .containsExactlyInAnyOrder(publicPost.getId(), subscribersPost.getId(), privatePost.getId(),
                        draft.getId(), blinded.getId());

        sql("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", owner.getId());
        assertThat(ids(PostSpecifications.visibleTo(other.getId(), now))).isEmpty();
    }

    private List<Long> ids(Specification<Post> condition) {
        return postRepository.findAll(PostSpecifications.inBlog(blog.getId()).and(condition)).stream()
                .map(Post::getId)
                .toList();
    }

    private Post published(Visibility visibility) {
        return postRepository.save(Post.published(blog, null, "제목", "<p>본문</p>", "본문", visibility, null,
                LocalDateTime.now()));
    }

    /** 아직 기능이 없는 상태 변경은 SQL로 하고, 영속성 컨텍스트를 비워 다시 읽게 한다. */
    private void sql(String sql, Object... args) {
        entityManager.flush();
        jdbcTemplate.update(sql, args);
        entityManager.clear();
    }

}
