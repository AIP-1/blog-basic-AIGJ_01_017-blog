package com.nhnacademy.blog.search.application;

import com.nhnacademy.blog.blog.application.PrimaryBlogAddresses;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.image.application.ProfileImages;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 검색 결과에 구독자 수·사진·주인의 대표 블로그를 붙인다 (T065, SRCH-02).
 * 한 페이지(10개)의 값을 종류마다 쿼리 한 번으로 읽는다(N+1 방지).
 */
@Service
public class BlogSearchService {

    private final SearchService searchService;
    private final SubscriptionRepository subscriptionRepository;
    private final ProfileImages profileImages;
    private final PrimaryBlogAddresses primaryBlogAddresses;

    public BlogSearchService(SearchService searchService, SubscriptionRepository subscriptionRepository,
                             ProfileImages profileImages, PrimaryBlogAddresses primaryBlogAddresses) {
        this.searchService = searchService;
        this.subscriptionRepository = subscriptionRepository;
        this.profileImages = profileImages;
        this.primaryBlogAddresses = primaryBlogAddresses;
    }

    @Transactional(readOnly = true)
    public Page<FoundBlog> search(Long viewerId, String query, PageQuery page) {
        Page<Blog> blogs = searchService.searchBlogs(query, page);
        List<Blog> content = blogs.getContent();
        Map<Long, Long> subscribers = content.isEmpty() ? Map.of()
                : subscriptionRepository.countByBlogIds(content.stream().map(Blog::getId).toList()).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, String> photos = profileImages.thumbnailUrls(content.stream()
                .flatMap(blog -> Stream.of(blog.getProfileImageId(), blog.getMember().getProfileImageId()))
                .toList());
        Map<Long, String> primaries = primaryBlogAddresses.of(
                content.stream().map(blog -> blog.getMember().getId()).distinct().toList(), viewerId);
        return blogs.map(blog -> new FoundBlog(blog, subscribers.getOrDefault(blog.getId(), 0L),
                photos.get(blog.getProfileImageId()), photos.get(blog.getMember().getProfileImageId()),
                primaries.get(blog.getMember().getId())));
    }

}
