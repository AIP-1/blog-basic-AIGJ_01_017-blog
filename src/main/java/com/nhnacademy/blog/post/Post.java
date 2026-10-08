package com.nhnacademy.blog.post;

import com.nhnacademy.blog.blog.Blog;
import com.nhnacademy.blog.category.Category;
import com.nhnacademy.blog.global.entity.BaseTimeEntity;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;

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

    protected Post() {
    }

    private Post(Blog blog, Category category, String title, String contentHtml, String summary,
                 PostStatus status, Visibility visibility, Topic topic) {
        this.blog = blog;
        this.category = category;
        this.title = title;
        this.contentHtml = contentHtml;
        this.summary = summary;
        this.status = status;
        this.visibility = visibility;
        this.topic = topic;
        this.commentAllowed = true;
    }

    /** 임시저장 글. */
    public static Post draft(Blog blog, Category category, String title, String contentHtml, String summary,
                             Visibility visibility, Topic topic) {
        return new Post(blog, category, title, contentHtml, summary, PostStatus.DRAFT, visibility, topic);
    }

    /** 바로 발행한 글. */
    public static Post published(Blog blog, Category category, String title, String contentHtml, String summary,
                                 Visibility visibility, Topic topic, LocalDateTime publishedAt) {
        Post post = new Post(blog, category, title, contentHtml, summary, PostStatus.PUBLISHED, visibility, topic);
        post.publishedAt = publishedAt;
        return post;
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
