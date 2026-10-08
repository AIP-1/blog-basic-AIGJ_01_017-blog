package com.nhnacademy.blog.support;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.category.domain.Category;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.Visibility;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 테스트용 글. 글쓰기 API(스텝 5) 전이라 저장소로 바로 넣고, 숨김·삭제 같은 상태는 SQL로 바꾼다.
 */
@Component
public class TestPosts {

    private final PostRepository postRepository;
    private final JdbcTemplate jdbcTemplate;

    public TestPosts(PostRepository postRepository, JdbcTemplate jdbcTemplate) {
        this.postRepository = postRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Post published(Blog blog, Visibility visibility) {
        return published(blog, null, visibility, LocalDateTime.now());
    }

    public Post published(Blog blog, Category category, Visibility visibility, LocalDateTime publishedAt) {
        return postRepository.save(Post.published(blog, category, "제목", "<p>본문</p>", "본문", visibility, null,
                publishedAt));
    }

    public Post draft(Blog blog) {
        return postRepository.save(Post.draft(blog, null, "임시", "<p>임시</p>", "임시", Visibility.PUBLIC, null));
    }

    public void blind(Post post) {
        jdbcTemplate.update("UPDATE post SET is_blinded = 1 WHERE id = ?", post.getId());
    }

    public void delete(Post post) {
        jdbcTemplate.update("UPDATE post SET deleted_at = NOW() WHERE id = ?", post.getId());
    }

}
