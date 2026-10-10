package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지의 내 블로그 (BLOG-08): 활성 블로그 목록과 대표 블로그 바꾸기.
 * 두 번째 블로그부터는 마이페이지의 "블로그 만들기 (n/5)"로 개설 화면에 간다(BLOG-01, 활성 5개 한도).
 */
@Service
public class MyBlogService {

    private final BlogRepository blogRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;

    public MyBlogService(BlogRepository blogRepository, MemberRepository memberRepository,
                         PostRepository postRepository) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
    }

    /** 내 활성 블로그, 만든 순서. 글 수는 쿼리 한 번으로 센다. */
    @Transactional(readOnly = true)
    public List<MyBlog> myBlogs(Long memberId) {
        List<Blog> blogs = blogRepository.findActiveByMemberId(memberId);
        if (blogs.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        postRepository.countPublishedByBlogIds(blogs.stream().map(Blog::getId).toList())
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return blogs.stream().map(blog -> new MyBlog(blog, counts.getOrDefault(blog.getId(), 0L))).toList();
    }

    /**
     * 대표 블로그를 바꾼다. 내 활성 블로그가 아니면 400(fieldErrors blogId).
     * 회원 행을 잠가 같은 회원의 동시 변경·개설과 줄을 세운다(BlogService.open과 같은 잠금).
     * 대표는 회원마다 하나라(UNIQUE primary_owner_id) 옛 대표를 먼저 끄고 flush한 뒤 새 대표를 켠다.
     */
    @Transactional
    public void changePrimary(Long memberId, Long blogId) {
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        List<Blog> blogs = blogRepository.findActiveByMemberId(memberId);
        Blog target = blogs.stream().filter(blog -> blog.getId().equals(blogId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField("blogId", "내 블로그를 골라 주세요."));
        if (target.isPrimary()) {
            return;
        }
        blogs.stream().filter(Blog::isPrimary).forEach(blog -> blog.markPrimary(false));
        blogRepository.flush();
        target.markPrimary(true);
    }

}
