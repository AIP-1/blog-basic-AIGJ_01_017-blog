package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.manage.domain.BlogStatsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새벽 통계 작업 (T082 MNG-03, T079 조회 기록 정리). 매일 0시 10분(서버 시간대 Asia/Seoul)에
 * ① 지난 7일을 다시 모으고(blog_daily_stat, blog_referrer_daily) ② 누적 방문자를 다시 계산하고
 * ③ 7일 지난 방문 기록·조회 기록을 지운다.
 * 어제 하루만 모으지 않고 7일을 다시 모으는 이유: 서버가 꺼져 있던 날이 있어도 다음 새벽에 저절로 채워지게(모으기는 지우고 다시 넣어
 * 몇 번 돌려도 같다). 방문 기록을 7일 남기므로 7일 안은 언제든 다시 모을 수 있다.
 * 스케줄러가 부르는 run()에 트랜잭션을 건다(같은 객체 안 호출은 프록시를 거치지 않는다, 학습 문서 52).
 */
@Component
public class StatsAggregator {

    private static final Logger log = LoggerFactory.getLogger(StatsAggregator.class);
    static final int KEEP_DAYS = 7;

    private final BlogStatsRepository blogStatsRepository;
    private final Clock clock;

    public StatsAggregator(BlogStatsRepository blogStatsRepository, Clock clock) {
        this.blogStatsRepository = blogStatsRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.scheduling.stats-cron:0 10 0 * * *}")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now(clock);
        for (int back = KEEP_DAYS; back >= 1; back--) {
            blogStatsRepository.aggregateDay(today.minusDays(back));
        }
        blogStatsRepository.refreshTotals();
        int visits = blogStatsRepository.deleteVisitsBefore(today.minusDays(KEEP_DAYS));
        // 블로거 순위는 "정시 기준 7일"을 보므로 지금 기준 7일보다 최대 1시간 앞의 기록까지 필요하다
        int views = blogStatsRepository.deleteViewsBefore(LocalDateTime.now(clock).minusDays(KEEP_DAYS).minusHours(1));
        log.info("통계 모으기 끝: 지운 방문 기록 {}행, 조회 기록 {}행", visits, views);
    }

}
