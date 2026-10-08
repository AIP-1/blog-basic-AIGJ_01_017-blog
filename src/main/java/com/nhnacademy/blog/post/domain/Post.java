package com.nhnacademy.blog.post.domain;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.global.entity.BaseTimeEntity;
import com.nhnacademy.blog.tag.domain.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /** image.id. 이미지 엔티티는 스텝 7에서 만든다. */
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

    /** 글에 단 태그 (TAG-01). 연결 테이블 post_tag(post_id, tag_id). 글당 10개는 TagNames가 검사한다. */
    @ManyToMany
    @JoinTable(name = "post_tag",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

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

    /** 태그를 통째로 바꾼다. 빠진 태그는 연결만 끊기고 블로그 태그는 남는다. */
    public void replaceTags(Collection<Tag> newTags) {
        tags.clear();
        tags.addAll(newTags);
    }

    /** 태그 이름, 가나다순. 트랜잭션 안에서 불러야 한다(지연 로딩). */
    public List<String> tagNames() {
        return tags.stream().map(Tag::getName).sorted(Comparator.naturalOrder()).toList();
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

}
