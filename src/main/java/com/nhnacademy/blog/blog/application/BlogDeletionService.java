package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogContentCleaner;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.post.application.PostDeletedEvent;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 삭제 (T107, BLOG-07). 옮기지 않은 글이 함께 지워진다는 미리보기, 주소를 다시 입력하는 확인, 대표 블로그는 409.
 * 블로그 행은 남아 주소를 다시 쓸 수 없고(영구 예약), 이사 연결도 그대로다. 회원 탈퇴(AUTH-06)도 이것으로 블로그를 지운다.
 */
@Service
public class BlogDeletionService {

    private final BlogRepository blogRepository;
    private final PostRepository postRepository;
    private final BlogContentCleaner blogContentCleaner;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BlogDeletionService(BlogRepository blogRepository, PostRepository postRepository,
                               BlogContentCleaner blogContentCleaner, ApplicationEventPublisher events, Clock clock) {
        this.blogRepository = blogRepository;
        this.postRepository = postRepository;
        this.blogContentCleaner = blogContentCleaner;
        this.events = events;
        this.clock = clock;
    }

    /** 함께 지워질 글 수(지우지 않은 글 전부: 발행·임시저장·예약). */
    @Transactional(readOnly = true)
    public Preview preview(Blog blog) {
        return new Preview(postRepository.findLiveIdsByBlogId(blog.getId()).size(), blog.isPrimary());
    }

    /** 주인이 지운다. 주소가 다르면 400(confirmAddress), 대표 블로그면 409 PRIMARY_BLOG. */
    @Transactional
    public void delete(Blog blog, String confirmAddress) {
        if (confirmAddress == null || !confirmAddress.strip().equals(blog.getAddress())) {
            throw BusinessException.invalidField("confirmAddress", "블로그 주소를 똑같이 입력해 주세요.");
        }
        if (blog.isPrimary()) {
            throw new BusinessException(ErrorCode.PRIMARY_BLOG);
        }
        deleteWithPosts(blog.getId());
    }

    /** 블로그와 남은 글을 지운다(대표 여부를 보지 않음, 탈퇴에서 부른다). */
    @Transactional
    public void deleteWithPosts(Long blogId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> postIds = postRepository.findLiveIdsByBlogId(blogId);
        blogContentCleaner.deletePosts(blogId, now);
        blogRepository.findById(blogId).orElseThrow().delete(now);
        postIds.forEach(id -> events.publishEvent(new PostDeletedEvent(id)));
    }

    public record Preview(long remainingPostCount, boolean isPrimary) {
    }

}
