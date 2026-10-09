package com.nhnacademy.blog.recommend.application;

import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.recommend.domain.PostEmbeddingRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 글 임베딩 만들기·지우기 (T069b, R-02). 발행 글이면 공개 범위와 상관없이 만든다(로컬 모델이라 글이 밖으로 나가지 않는다).
 * 볼 수 있는지는 추천할 때 거른다(SimilarPostService).
 * <p>
 * 임베딩 서버(Ollama)가 꺼져 있거나 실패해도 예외를 밖으로 던지지 않고 경고만 남긴다. 추천은 부가 기능이라
 * 글 쓰기나 서버 시작을 막지 않는다. 그 글은 임베딩이 없어 추천이 빈 배열이 된다.
 */
@Service
public class PostEmbeddingService {

    /** 임베딩에 넣는 본문 글자 수. 글 앞부분이 주제를 잘 나타내고, 모델 입력 길이와 계산 시간을 줄인다. */
    static final int MAX_TEXT_LENGTH = 2000;

    private static final Logger log = LoggerFactory.getLogger(PostEmbeddingService.class);

    private final PostRepository postRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final EmbeddingClient embeddingClient;

    public PostEmbeddingService(PostRepository postRepository, PostEmbeddingRepository postEmbeddingRepository,
                                EmbeddingClient embeddingClient) {
        this.postRepository = postRepository;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.embeddingClient = embeddingClient;
    }

    /** 이 글의 임베딩을 지금 내용으로 다시 만든다. 지웠거나 발행 글이 아니면 임베딩을 지운다. */
    public void refresh(long postId) {
        try {
            Post post = postRepository.findById(postId).orElse(null);
            if (post == null || post.isDeleted() || post.getStatus() != PostStatus.PUBLISHED) {
                postEmbeddingRepository.delete(postId);
                return;
            }
            postEmbeddingRepository.save(postId, embeddingClient.embed(text(post)));
        } catch (RuntimeException e) {
            log.warn("글 {}의 임베딩을 만들지 못했습니다: {}", postId, e.toString());
        }
    }

    public void remove(long postId) {
        try {
            postEmbeddingRepository.delete(postId);
        } catch (RuntimeException e) {
            log.warn("글 {}의 임베딩을 지우지 못했습니다: {}", postId, e.toString());
        }
    }

    /**
     * 임베딩 표를 발행 글과 맞춘다. 임베딩이 없는 발행 글은 만들고, 지워진 글의 임베딩은 지운다.
     * 서버가 뜰 때(이미 있던 글, Ollama가 꺼져 있던 사이의 글) 한 번 돈다. 만든 수를 돌려준다.
     */
    public int sync() {
        try {
            List<Long> published = postRepository.findPublishedIds();
            Set<Long> embedded = new HashSet<>(postEmbeddingRepository.findAllPostIds());
            Set<Long> stale = new HashSet<>(embedded);
            published.forEach(stale::remove);
            postEmbeddingRepository.deleteAll(stale);
            int created = 0;
            for (Long postId : published) {
                if (!embedded.contains(postId)) {
                    refresh(postId);
                    created++;
                }
            }
            return created;
        } catch (RuntimeException e) {
            log.warn("임베딩을 맞추지 못했습니다: {}", e.toString());
            return 0;
        }
    }

    /** 제목 + 본문 글자 앞부분. 제목이 주제를 가장 잘 말해 주므로 앞에 둔다. */
    static String text(Post post) {
        String body = post.getContentText() == null ? "" : post.getContentText();
        String text = post.getTitle() + "\n" + body;
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

}
