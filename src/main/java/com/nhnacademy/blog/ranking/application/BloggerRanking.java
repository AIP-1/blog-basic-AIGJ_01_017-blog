package com.nhnacademy.blog.ranking.application;

import com.nhnacademy.blog.home.application.PopularProperties;
import com.nhnacademy.blog.ranking.domain.BlogScoreRepository;
import java.time.LocalDateTime;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 인기 블로거 순위 계산 (T079, HOME-04, R-13). "1시간마다 갱신"을 시간 칸으로 만든다: 순위는 [정시 - 7일, 정시) 동안의 활동으로
 * 계산하고 기준 시각을 그 정시로 둔다. 같은 시간 칸 안에서는 언제 다시 계산해도 같은 순위·같은 기준 시각이라,
 * 캐시가 비워져 다시 계산돼도 홈·랭킹 전체보기·추천이 같은 순위를 본다. 정시가 바뀌면 새 칸이 되어 순위가 바뀐다.
 * 계산 결과는 Redis 캐시에 칸마다 둔다(키 = 정시). 캐시는 프록시가 가로채므로 부르는 쪽은 다른 빈(RankingService)이다.
 */
@Component
public class BloggerRanking {

    public static final String CACHE = "popularBloggers";
    /** 순위 후보 수. 랭킹 전체보기가 100위까지다. */
    public static final int CANDIDATES = 100;

    private final BlogScoreRepository blogScoreRepository;
    private final PopularProperties popularProperties;
    private final RankingProperties rankingProperties;

    public BloggerRanking(BlogScoreRepository blogScoreRepository, PopularProperties popularProperties,
                          RankingProperties rankingProperties) {
        this.blogScoreRepository = blogScoreRepository;
        this.popularProperties = popularProperties;
        this.rankingProperties = rankingProperties;
    }

    @Cacheable(cacheNames = CACHE, key = "#hourStart.toString()", sync = true)
    public BloggerSnapshot snapshot(LocalDateTime hourStart) {
        return new BloggerSnapshot(hourStart, blogScoreRepository.topScores(
                hourStart.minus(rankingProperties.blogWindow()), hourStart,
                new BlogScoreRepository.Weights(popularProperties.viewWeight(), popularProperties.likeWeight(),
                        popularProperties.commentWeight(), rankingProperties.subscribeWeight()),
                CANDIDATES));
    }

}
