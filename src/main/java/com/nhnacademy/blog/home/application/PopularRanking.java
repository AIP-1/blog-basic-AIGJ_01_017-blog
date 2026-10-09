package com.nhnacademy.blog.home.application;

import com.nhnacademy.blog.home.domain.PopularScoreRepository;
import com.nhnacademy.blog.post.domain.Topic;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 인기 점수 계산 (T063, HOME-02, R-12). 계산 결과는 Redis 캐시에 5분(spring.cache.redis.time-to-live) 둔다.
 * 5분 안에 다시 부르면 이 메서드 본문은 실행되지 않고 Redis에 둔 값이 돌아온다.
 * <p>
 * 캐시는 스프링이 만든 프록시가 메서드 호출을 가로채서 동작한다. 그래서 같은 클래스 안에서 this.snapshot()으로 부르면
 * 프록시를 거치지 않아 캐시가 안 된다. 부르는 쪽(HomeService)과 클래스를 나눈 이유다.
 */
@Component
public class PopularRanking {

    public static final String CACHE = "popularPosts";
    public static final String TOPIC_CACHE = "topicPosts";

    /**
     * 순위 후보 수. 읽을 때 블로그 이용 제한 등으로 빠지는 글이 있어도 10개를 채울 수 있게 넉넉히 둔다.
     * 랭킹 전체보기(HOME-05, 100위까지)가 생기면 같은 값을 쓴다.
     */
    static final int CANDIDATES = 100;

    /** 주제별 글은 6개만 보이므로 후보도 적게 둔다. 모자라면 최신 글로 채운다(HomeService). */
    static final int TOPIC_CANDIDATES = 30;

    private final PopularScoreRepository popularScoreRepository;
    private final PopularProperties properties;
    private final Clock clock;

    public PopularRanking(PopularScoreRepository popularScoreRepository, PopularProperties properties, Clock clock) {
        this.popularScoreRepository = popularScoreRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * sync = true: 캐시가 비었을 때 요청 여러 개가 동시에 와도 한 요청만 계산하고 나머지는 그 결과를 기다린다
     * (한 서버 안에서). 비어 있는 순간 모든 요청이 무거운 집계를 한꺼번에 돌리지 않게 한다.
     */
    @Cacheable(cacheNames = CACHE, key = "'home'", sync = true)
    public PopularSnapshot snapshot() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new PopularSnapshot(now, popularScoreRepository.topScores(now.minus(properties.window()),
                properties.viewWeight(), properties.likeWeight(), properties.commentWeight(), CANDIDATES));
    }

    /**
     * 주제별 인기 순위 (T064, HOME-03). 홈 인기 글과 같은 점수, 같은 5분 캐시이고 주제마다 키가 따로다
     * (Redis 키 blog:topicPosts::IT_DEV 등).
     */
    @Cacheable(cacheNames = TOPIC_CACHE, key = "#topic.name()", sync = true)
    public PopularSnapshot topicSnapshot(Topic topic) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new PopularSnapshot(now, popularScoreRepository.topScoresInTopic(topic.name(),
                now.minus(properties.window()), properties.viewWeight(), properties.likeWeight(),
                properties.commentWeight(), TOPIC_CANDIDATES));
    }

}
