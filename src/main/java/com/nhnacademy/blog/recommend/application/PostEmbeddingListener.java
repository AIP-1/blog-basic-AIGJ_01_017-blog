package com.nhnacademy.blog.recommend.application;

import com.nhnacademy.blog.post.application.PostContentChangedEvent;
import com.nhnacademy.blog.post.application.PostDeletedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 글 이벤트를 받아 임베딩을 고친다 (T069b).
 * <ul>
 *   <li>{@code @TransactionalEventListener}: 기본이 AFTER_COMMIT이라, 글 저장 트랜잭션이 <b>커밋된 뒤에만</b> 받는다.
 *       저장이 롤백되면 임베딩도 만들지 않는다. 커밋 뒤라 다른 트랜잭션에서 글을 읽어도 새 내용이 보인다.</li>
 *   <li>{@code @Async}: 다른 스레드에서 돈다. 글 저장 응답이 임베딩 계산(수백 ms~수 초)을 기다리지 않는다.</li>
 * </ul>
 */
@Component
public class PostEmbeddingListener {

    private final PostEmbeddingService postEmbeddingService;

    public PostEmbeddingListener(PostEmbeddingService postEmbeddingService) {
        this.postEmbeddingService = postEmbeddingService;
    }

    @Async
    @TransactionalEventListener
    public void onContentChanged(PostContentChangedEvent event) {
        postEmbeddingService.refresh(event.postId());
    }

    @Async
    @TransactionalEventListener
    public void onDeleted(PostDeletedEvent event) {
        postEmbeddingService.remove(event.postId());
    }

    /** 서버가 다 뜬 뒤 한 번, 임베딩이 없는 발행 글을 채운다. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        postEmbeddingService.sync();
    }

}
