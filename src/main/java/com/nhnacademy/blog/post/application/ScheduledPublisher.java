package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.post.domain.PostStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 발행 (T103, POST-13). 1분마다 예약 시각이 지난 글을 발행한다. 그 예약 시각이 처음 발행 시각이다
 * (작업이 늦게 돌아도 목록 순서가 정한 시각대로). 발행한 글은 바로 발행한 글처럼 추천 임베딩을 만든다.
 * 서버가 여러 대가 되면 같은 글을 두 번 발행하지 않게 잠금이 필요하지만, 지금은 한 대라 status 조건으로 충분하다.
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

    @Scheduled(fixedDelayString = "${app.scheduling.publish-delay:60000}")
    public void run() {
        int published = publishDue();
        if (published > 0) {
            log.info("예약 발행 {}개", published);
        }
    }

    /** 예약 시각이 지금이거나 지난 글을 발행하고 그 수를 돌려준다. 지운 글은 발행하지 않는다. */
    @Transactional
    public int publishDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Post> due = postRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("status"), PostStatus.SCHEDULED),
                cb.isNull(root.get("deletedAt")),
                cb.lessThanOrEqualTo(root.get("scheduledAt"), now)));
        for (Post post : due) {
            post.publishScheduled();
            events.publishEvent(new PostContentChangedEvent(post.getId()));
        }
        return due.size();
    }

}
