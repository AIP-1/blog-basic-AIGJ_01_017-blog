package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.ViewLog;
import com.nhnacademy.blog.post.domain.ViewLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 기록과 조회수 (T053, POST-09). 같은 조회자가 5분 안에 다시 열면 세지 않는다(Q2).
 * 볼 수 있는 글인지는 부르는 쪽이 먼저 확인한다(PostReadService.readable, 다른 트랜잭션).
 */
@Service
public class ViewService {

    /** 이 시간 안에 같은 조회자가 다시 열면 한 번으로 본다 (spec Q2). */
    public static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(5);

    private final PostRepository postRepository;
    private final ViewLogRepository viewLogRepository;
    private final Clock clock;

    public ViewService(PostRepository postRepository, ViewLogRepository viewLogRepository, Clock clock) {
        this.postRepository = postRepository;
        this.viewLogRepository = viewLogRepository;
        this.clock = clock;
    }

    /**
     * 5분 안에 본 기록이 없으면 기록을 남기고 조회수를 올린다. 셌으면 true.
     * <p>
     * 같은 사람이 새로고침을 연타하면 요청 여러 개가 동시에 "기록 없음"을 보고 둘 다 셀 수 있다.
     * 그래서 맨 먼저 글 행을 잠가(SELECT ... FOR UPDATE) 같은 글의 조회 기록을 차례로 처리한다.
     * PostgreSQL 기본 격리(READ COMMITTED)는 문장마다 그때까지 커밋된 것을 보므로, 잠금을 기다린 뒤의 확인은 앞 요청이 넣은 기록을 본다.
     * 잠금을 첫 문장으로 두는 것은 격리 수준을 REPEATABLE READ로 올려도(첫 문장 때의 스냅샷을 계속 본다) 맞게 하려는 것이다.
     */
    @Transactional
    public boolean record(Long postId, String viewerKey) {
        postRepository.lockById(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        if (viewLogRepository.existsByPostIdAndViewerKeyAndViewedAtAfter(postId, viewerKey,
                now.minus(DUPLICATE_WINDOW))) {
            return false;
        }
        viewLogRepository.save(ViewLog.of(postRepository.getReferenceById(postId), viewerKey, now));
        postRepository.increaseViewCount(postId);
        return true;
    }

}
