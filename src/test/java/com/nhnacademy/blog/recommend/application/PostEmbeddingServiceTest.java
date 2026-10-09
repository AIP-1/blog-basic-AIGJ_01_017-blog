package com.nhnacademy.blog.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 임베딩 서버가 꺼져 있어도 글 쓰기·서버 시작을 막지 않는다 (T069b, R-02). 넣는 글자는 제목 + 본문 앞 2,000자.
 */
class PostEmbeddingServiceTest {

    PostRepository postRepository = mock(PostRepository.class);
    PostEmbeddingRepository postEmbeddingRepository = mock(PostEmbeddingRepository.class);

    @Test
    void embeddingServerFailureIsSwallowed() {
        Post post = publishedPost("제목", "본문");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        EmbeddingClient down = text -> {
            throw new IllegalStateException("Connection refused");
        };
        PostEmbeddingService service = new PostEmbeddingService(postRepository, postEmbeddingRepository, down);

        assertThatCode(() -> service.refresh(1L)).doesNotThrowAnyException();
        verify(postEmbeddingRepository, never()).save(anyLong(), any());
    }

    @Test
    void textIsTitleThenBodyCutAtTwoThousandChars() {
        Post post = publishedPost("제목", "가".repeat(5000));

        String text = PostEmbeddingService.text(post);

        assertThat(text).startsWith("제목\n가").hasSize(PostEmbeddingService.MAX_TEXT_LENGTH);
    }

    private static Post publishedPost(String title, String body) {
        Post post = mock(Post.class);
        when(post.getTitle()).thenReturn(title);
        when(post.getContentText()).thenReturn(body);
        when(post.getStatus()).thenReturn(PostStatus.PUBLISHED);
        return post;
    }

}
