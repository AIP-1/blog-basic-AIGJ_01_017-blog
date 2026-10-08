package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 대표 블로그 주소 (MemberSummary.primaryBlogAddress, BLOG-08 닉네임 링크).
 * 대표 블로그가 없거나 보는 사람이 볼 수 없으면(이용 제한, 주인 정지) 없는 것으로 친다.
 */
@Component
public class PrimaryBlogAddresses {

    private final BlogRepository blogRepository;
    private final BlogVisibilityPolicy blogVisibilityPolicy;

    public PrimaryBlogAddresses(BlogRepository blogRepository, BlogVisibilityPolicy blogVisibilityPolicy) {
        this.blogRepository = blogRepository;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
    }

    /** 블로그 주인의 대표 블로그 주소. 이 블로그가 대표면 바로 이 주소다. */
    @Transactional(readOnly = true)
    public String ofOwner(Blog blog, Long viewerId) {
        if (blog.isPrimary()) {
            return blog.getAddress();
        }
        return of(List.of(blog.getMember().getId()), viewerId).get(blog.getMember().getId());
    }

    /** 회원 id → 대표 블로그 주소. 없거나 볼 수 없는 회원은 맵에 없다. 쿼리 한 번이다. */
    @Transactional(readOnly = true)
    public Map<Long, String> of(Collection<Long> memberIds, Long viewerId) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return blogRepository.findPrimaryByMemberIds(memberIds).stream()
                .filter(blog -> blogVisibilityPolicy.canView(blog, viewerId))
                .collect(Collectors.toMap(blog -> blog.getMember().getId(), Blog::getAddress));
    }

}
