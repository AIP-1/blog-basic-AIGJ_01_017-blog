package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.visibility.PostSpecifications;
import com.nhnacademy.blog.manage.domain.BlogStatsRepository;
import com.nhnacademy.blog.manage.domain.ReferrerType;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문 통계 읽기 (T083, MNG-03). 어제까지는 새벽에 모은 값, 오늘은 방문 기록을 바로 센다. 주인 검사는 컨트롤러가 먼저 했다.
 */
@Service
public class BlogStatsService {

    public static final int POPULAR_SIZE = 10;
    public static final int MAX_DAYS = 400;
    private static final int SITE_LIMIT = 20;
    private static final Sort MOST_VIEWED = Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("id"));
    private static final Sort LATEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final BlogStatsRepository statsRepository;
    private final PostRepository postRepository;
    private final Clock clock;

    public BlogStatsService(BlogStatsRepository statsRepository, PostRepository postRepository, Clock clock) {
        this.statsRepository = statsRepository;
        this.postRepository = postRepository;
        this.clock = clock;
    }

    /** 관리 홈: 오늘·어제·누적 방문자(누적 = 어제까지 모은 값 + 오늘)와 최근 글 5개(조회 수와 함께). */
    @Transactional(readOnly = true)
    public Summary summary(Blog blog) {
        LocalDate today = LocalDate.now(clock);
        long todayVisitors = statsRepository.liveDay(blog.getId(), today).visitors();
        List<BlogStatsRepository.Day> yesterday = statsRepository.dailyStats(blog.getId(), today.minusDays(1),
                today.minusDays(1));
        long yesterdayVisitors = yesterday.isEmpty()
                ? statsRepository.liveDay(blog.getId(), today.minusDays(1)).visitors()
                : yesterday.getFirst().visitors();
        List<Post> recent = postRepository.findBy(
                PostSpecifications.listedIn(blog, blog.getMember().getId(), LocalDateTime.now(clock)),
                query -> query.sortBy(LATEST).limit(5).all());
        return new Summary(todayVisitors, yesterdayVisitors, blog.getTotalVisitorCount() + todayVisitors, recent);
    }

    /**
     * 방문자 그래프. unit: day(기본 최근 14일), week(최근 12주, 월요일 시작), month(최근 12달). from·to(날짜)로 바꿀 수 있다.
     * 값이 없는 칸도 0으로 채워 그래프 눈금이 빠지지 않게 한다. date는 칸의 첫날이다.
     */
    @Transactional(readOnly = true)
    public List<Point> visitors(Blog blog, String rawUnit, String rawFrom, String rawTo) {
        String unit = rawUnit == null || rawUnit.isBlank() ? "day" : rawUnit;
        LocalDate today = LocalDate.now(clock);
        LocalDate to = date("to", rawTo, today);
        LocalDate from = date("from", rawFrom, switch (unit) {
            case "day" -> to.minusDays(13);
            case "week" -> to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11);
            case "month" -> to.withDayOfMonth(1).minusMonths(11);
            default -> throw BusinessException.invalidField("unit", "day, week, month 중 하나입니다.");
        });
        if (from.isAfter(to)) {
            throw BusinessException.invalidField("from", "시작 날짜가 끝 날짜보다 뒤입니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw BusinessException.invalidField("from", "기간은 " + MAX_DAYS + "일까지입니다.");
        }
        Map<LocalDate, BlogStatsRepository.Day> days = new HashMap<>();
        statsRepository.dailyStats(blog.getId(), from, to).forEach(day -> days.put(day.date(), day));
        if (!today.isBefore(from) && !today.isAfter(to)) {
            days.put(today, statsRepository.liveDay(blog.getId(), today));
        }
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate key = switch (unit) {
                case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case "month" -> date.withDayOfMonth(1);
                default -> date;
            };
            long[] sums = buckets.computeIfAbsent(key, k -> new long[2]);
            BlogStatsRepository.Day day = days.get(date);
            if (day != null) {
                sums[0] += day.visitors();
                sums[1] += day.views();
            }
        }
        List<Point> points = new ArrayList<>();
        buckets.forEach((date, sums) -> points.add(new Point(date, sums[0], sums[1])));
        return points;
    }

    /** 인기 글 10. range=all은 누적 조회 수(post.view_count), 7d는 최근 7일 조회 기록 수. */
    @Transactional(readOnly = true)
    public List<PopularPost> popularPosts(Blog blog, String range) {
        if (range == null || range.isBlank() || range.equals("all")) {
            return postRepository.findBy(
                            PostSpecifications.listedIn(blog, blog.getMember().getId(), LocalDateTime.now(clock)),
                            query -> query.sortBy(MOST_VIEWED).limit(POPULAR_SIZE).all())
                    .stream().map(post -> new PopularPost(post.getId(), post.getTitle(), post.getViewCount())).toList();
        }
        if (range.equals("7d")) {
            return statsRepository.recentPopular(blog.getId(), LocalDateTime.now(clock).minusDays(7), POPULAR_SIZE)
                    .stream().map(row -> new PopularPost(row.postId(), row.title(), row.views())).toList();
        }
        throw BusinessException.invalidField("range", "all 또는 7d입니다.");
    }

    /** 유입 경로: 종류별 합과 사이트별 상위 20. 기본은 최근 30일(오늘 포함). */
    @Transactional(readOnly = true)
    public Referrers referrers(Blog blog, String rawFrom, String rawTo) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = date("to", rawTo, today);
        LocalDate from = date("from", rawFrom, to.minusDays(29));
        if (from.isAfter(to)) {
            throw BusinessException.invalidField("from", "시작 날짜가 끝 날짜보다 뒤입니다.");
        }
        List<BlogStatsRepository.Referrer> rows = new ArrayList<>(statsRepository.referrers(blog.getId(), from, to));
        if (!today.isBefore(from) && !today.isAfter(to)) {
            rows.addAll(statsRepository.liveReferrers(blog.getId(), today));
        }
        Map<ReferrerType, Long> byType = new EnumMap<>(ReferrerType.class);
        Map<String, Site> bySite = new HashMap<>();
        for (BlogStatsRepository.Referrer row : rows) {
            byType.merge(row.type(), row.count(), Long::sum);
            String key = row.type() + " " + row.host();
            Site site = bySite.get(key);
            bySite.put(key, new Site(row.type(), row.host(), (site == null ? 0 : site.count()) + row.count()));
        }
        List<TypeCount> types = byType.entrySet().stream()
                .map(entry -> new TypeCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TypeCount::count).reversed())
                .toList();
        List<Site> sites = bySite.values().stream()
                .sorted(Comparator.comparingLong(Site::count).reversed().thenComparing(Site::host))
                .limit(SITE_LIMIT)
                .toList();
        return new Referrers(types, sites);
    }

    private static LocalDate date(String field, String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw BusinessException.invalidField(field, "날짜는 2026-10-11 꼴입니다.");
        }
    }

    public record Summary(long today, long yesterday, long total, List<Post> recentPosts) {
    }

    public record Point(LocalDate date, long visitorCount, long viewCount) {
    }

    public record PopularPost(Long postId, String title, long viewCount) {
    }

    public record TypeCount(ReferrerType type, long count) {
    }

    public record Site(ReferrerType type, String host, long count) {
    }

    public record Referrers(List<TypeCount> byType, List<Site> bySite) {
    }

}
