package com.nhnacademy.blog.tag.domain;

import com.nhnacademy.blog.post.domain.Post;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * 글에 단 태그 하나 (TAG-01, T038a). 연결 테이블 post_tag(post_id, tag_id)를 엔티티로 다룬다.
 * 예전에는 Post에 @ManyToMany로 매핑했다. 그러면 연결 테이블에 칸(단 시각, 순서 등)을 더할 수 없고,
 * 어떤 INSERT·DELETE가 나가는지 코드에서 보이지 않는다. 연결 행을 엔티티로 두면 둘 다 해결된다.
 * <p>
 * 키는 (post_id, tag_id) 두 칸이다. @MapsId는 "키의 이 칸은 이 연관의 id로 채운다"는 뜻이라, 칸을 두 번 매핑하지 않는다.
 * 글당 10개는 TagNames가 검사한다.
 */
@Entity
@Table(name = "post_tag")
public class PostTag {

    @EmbeddedId
    private PostTagId id = new PostTagId();

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @MapsId("tagId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    protected PostTag() {
    }

    private PostTag(Post post, Tag tag) {
        this.post = post;
        this.tag = tag;
    }

    /** 글에 태그를 단다. 저장은 글(Post.postTags의 cascade)이 한다. */
    public static PostTag of(Post post, Tag tag) {
        return new PostTag(post, tag);
    }

    public PostTagId getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public Tag getTag() {
        return tag;
    }

}
