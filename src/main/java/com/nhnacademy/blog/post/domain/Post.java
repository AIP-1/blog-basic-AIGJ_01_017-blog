package com.nhnacademy.blog.post.domain;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.global.entity.BaseTimeEntity;
import com.nhnacademy.blog.tag.domain.PostTag;
import com.nhnacademy.blog.tag.domain.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 글. id가 곧 글 주소 번호다({address}.blog.com/{id}). 이사하면 blog가 바뀐다.
 */
@Entity
@Table(name = "post")
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    /** null = 미분류. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** 주인이 고른 대표 이미지(image.id, POST-07). null이면 본문 첫 이미지가 대표다(PostThumbnails). */
    @Column(name = "thumbnail_image_id")
    private Long thumbnailImageId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 서버 정화(HtmlSanitizer)를 거친 본문. */
    @Column(name = "content_html", nullable = false, columnDefinition = "mediumtext")
    private String contentHtml;

    @Column(name = "summary", length = 300)
    private String summary;

    /** 본문에서 태그를 뺀 글자. 블로그 안 검색이 본다 (SRCH-01, research R-16). */
    @Column(name = "content_text", nullable = false, columnDefinition = "mediumtext")
    private String contentText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic", length = 20)
    private Topic topic;

    /** 처음 발행 시각. 정렬 기준이고 수정해도 바뀌지 않는다. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "is_comment_allowed", nullable = false)
    private boolean commentAllowed;

    @Column(name = "is_blinded", nullable = false)
    private boolean blinded;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 글에 단 태그 (TAG-01). 연결 테이블 post_tag의 행을 PostTag 엔티티로 다룬다(T038a).
     * cascade·orphanRemoval: 글에 PostTag를 넣으면 함께 INSERT, 목록에서 빼면 그 행만 DELETE된다.
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PostTag> postTags = new LinkedHashSet<>();

    protected Post() {
    }

    private Post(Blog blog, Category category, String title, PostBody body, PostStatus status, Visibility visibility,
                 Topic topic) {
        this.blog = blog;
        this.category = category;
        this.title = title;
        this.contentHtml = body.html();
        this.contentText = body.text();
        this.summary = body.summary();
        this.status = status;
        this.visibility = visibility;
        this.topic = topic;
        this.commentAllowed = true;
    }

    /** 임시저장 글. */
    public static Post draft(Blog blog, Category category, String title, PostBody body, Visibility visibility,
                             Topic topic) {
        return new Post(blog, category, title, body, PostStatus.DRAFT, visibility, topic);
    }

    /**
     * 예약 발행 글 (POST-13). 정한 시각 전에는 주인 말고 아무도 볼 수 없다(가시성 판단이 PUBLISHED만 남에게 보여 준다).
     * 그 시각에 ScheduledPublisher가 발행하고, 그 시각이 처음 발행 시각이다.
     */
    public static Post scheduled(Blog blog, Category category, String title, PostBody body, Visibility visibility,
                                 Topic topic, LocalDateTime scheduledAt) {
        Post post = new Post(blog, category, title, body, PostStatus.SCHEDULED, visibility, topic);
        post.scheduledAt = scheduledAt;
        return post;
    }

    /** 바로 발행한 글. */
    public static Post published(Blog blog, Category category, String title, PostBody body, Visibility visibility,
                                 Topic topic, LocalDateTime publishedAt) {
        Post post = new Post(blog, category, title, body, PostStatus.PUBLISHED, visibility, topic);
        post.publishedAt = publishedAt;
        return post;
    }

    /**
     * 글 수정 (POST-02). 주소(id), 처음 발행 시각, 수치는 그대로다. 수정 시각(updated_at)은 Auditing이 남긴다.
     * body.html은 정화를 거친 값이어야 한다.
     */
    public void edit(Category category, String title, PostBody body, Visibility visibility, Topic topic) {
        this.category = category;
        this.title = title;
        this.contentHtml = body.html();
        this.contentText = body.text();
        this.summary = body.summary();
        this.visibility = visibility;
        this.topic = topic;
    }

    /**
     * 임시저장 글을 발행한다 (POST-08). 지금이 처음 발행 시각이다. 이미 발행한 글이면 아무것도 바꾸지 않는다.
     */
    public void publish(LocalDateTime now) {
        if (status == PostStatus.PUBLISHED) {
            return;
        }
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = now;
        this.scheduledAt = null;
    }

    /** 아직 발행하지 않은 글(임시저장·예약)을 예약으로 바꾸거나 예약 시각을 바꾼다 (POST-13). 발행한 글은 PostService가 막는다. */
    public void schedule(LocalDateTime scheduledAt) {
        this.status = PostStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
    }

    /** 예약을 거두고 임시저장으로 (예약 글을 DRAFT로 저장할 때). */
    public void unschedule() {
        if (status == PostStatus.SCHEDULED) {
            this.status = PostStatus.DRAFT;
            this.scheduledAt = null;
        }
    }

    /**
     * 정한 시각이 되어 발행한다 (POST-13, contracts 글 저장 본문 status 표). 그 예약 시각이 처음 발행 시각이고
     * (작업이 몇십 초 늦게 돌아도 글 목록 순서는 정한 시각대로), 공개 범위는 공개로 바뀐다.
     */
    public void publishScheduled() {
        if (status != PostStatus.SCHEDULED) {
            return;
        }
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = scheduledAt;
        this.visibility = Visibility.PUBLIC;
        this.scheduledAt = null;
    }

    /** 댓글 허용·막기 (CMT-07). 막아도 이미 달린 댓글은 그대로 보인다. */
    public void changeCommentAllowed(boolean commentAllowed) {
        this.commentAllowed = commentAllowed;
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }

    /** 대표 이미지를 바꾼다. 본문에 든 이미지인지는 PostService가 확인했다. null이면 본문 첫 이미지. */
    public void changeThumbnail(Long imageId) {
        this.thumbnailImageId = imageId;
    }

    public boolean isDraft() {
        return status == PostStatus.DRAFT;
    }

    /**
     * 태그를 이 목록으로 바꾼다. 빠진 태그의 연결 행만 지우고 새 태그의 연결 행만 넣는다(그대로인 태그는 건드리지 않음).
     * 빠진 태그가 어느 글에도 남지 않으면 블로그 태그(tag 행)는 TagService.removeUnused가 지운다.
     */
    public void replaceTags(Collection<Tag> newTags) {
        Set<Long> wanted = newTags.stream().map(Tag::getId).collect(Collectors.toSet());
        postTags.removeIf(postTag -> !wanted.contains(postTag.getTag().getId()));
        Set<Long> current = postTags.stream().map(postTag -> postTag.getTag().getId()).collect(Collectors.toSet());
        newTags.stream()
                .filter(tag -> !current.contains(tag.getId()))
                .forEach(tag -> postTags.add(PostTag.of(this, tag)));
    }

    /** 단 태그의 id. 트랜잭션 안에서 불러야 한다(지연 로딩). */
    public Set<Long> tagIds() {
        return postTags.stream().map(postTag -> postTag.getTag().getId()).collect(Collectors.toSet());
    }

    /** 태그 이름, 가나다순. 트랜잭션 안에서 불러야 한다(지연 로딩). */
    public List<String> tagNames() {
        return postTags.stream().map(postTag -> postTag.getTag().getName())
                .sorted(Comparator.naturalOrder()).toList();
    }

    /** 공개 범위만 바꾼다 (POST-06). */
    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    /** 소프트 삭제 (POST-03). 행은 남고 사용자에게만 사라진다. */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Blog getBlog() {
        return blog;
    }

    public Category getCategory() {
        return category;
    }

    public Long getThumbnailImageId() {
        return thumbnailImageId;
    }

    public String getTitle() {
        return title;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public String getContentText() {
        return contentText;
    }

    public String getSummary() {
        return summary;
    }

    public PostStatus getStatus() {
        return status;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public Topic getTopic() {
        return topic;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public long getViewCount() {
        return viewCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public boolean isCommentAllowed() {
        return commentAllowed;
    }

    public boolean isBlinded() {
        return blinded;
    }

    /** 관리자 숨김·해제 (ADMIN-03). 사유는 moderation_log의 최신 BLIND 행에 있다. */
    public void changeBlinded(boolean blinded) {
        this.blinded = blinded;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

}
