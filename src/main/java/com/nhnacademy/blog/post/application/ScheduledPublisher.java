package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 발행 (T103, POST-13). 1분마다 예약 시각이 지난 글을 발행한다. 그 예약 시각이 처음 발행 시각이다
 * (작업이 늦게 돌아도 목록 순서가 정한 시각대로). 발행한 글은 바로 발행한 글처럼 추천 임베딩을 만든다.
 * 앱이 여러 대여도 각자 돌게 둔다. 글마다 조건을 단 UPDATE(PostRepository.publishIfDue)로 발행하므로 같은 글을 발행하는 쪽은
 * 하나뿐이고, 그쪽만 이벤트를 보낸다. 그사이 글쓴이가 고친 내용도 덮어쓰지 않는다.
 * 1분마다 도는 것은 SchedulingConfig가 켠다(테스트에서는 꺼 두고 publishDue를 직접 부른다).
 */
@Component
public class ScheduledPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublisher.class);

    private final PostRepository postRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ScheduledPublisher(PostRepository postRepository, ApplicationEventPublisher events, Clock clock) {
        this.postRepository = postRepository;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 스케줄러가 부르는 입구. publishDue를 같은 객체 안에서 부르면 Spring 프록시를 거치지 않아 publishDue의
     * {@code @Transactional}이 걸리지 않는다(트랜잭션 없이 UPDATE 쿼리를 부르면 실패한다. 엔티티를 고쳐 저장하던 예전에는
     * 바꾼 상태가 저장되지 않고 매분 같은 글을 "발행"했다). 그래서 이 메서드에도
     * 트랜잭션을 건다. 스케줄러는 프록시를 통해 이 메서드를 부른다.
     */
    @Scheduled(fixedDelayString = "${app.scheduling.publish-delay:60000}")
    @Transactional
    public void run() {
        int published = publishDue();
        if (published > 0) {
            log.info("예약 발행 {}개", published);
        }
    }

    /** 예약 시각이 지금이거나 지난 글을 발행하고, 이 호출이 발행한 수를 돌려준다. 지운 글은 발행하지 않는다. */
    @Transactional
    public int publishDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        int published = 0;
        for (Long id : postRepository.findDueScheduledIds(now)) {
            // 0이면 다른 앱이 먼저 발행했거나, 그사이 예약이 바뀌었다
            if (postRepository.publishIfDue(id, now) == 1) {
                events.publishEvent(new PostContentChangedEvent(id));
                published++;
            }
        }
        return published;
    }

}
