package com.nhnacademy.blog.support;

import com.nhnacademy.blog.global.host.BlogAddressResolver;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.member.domain.Member;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 테스트용 블로그. 이사·삭제·이용 제한 같은 상태는 아직 기능이 없어 SQL로 바꾼다.
 */
@Component
public class TestBlogs {

    private final BlogRepository blogRepository;
    private final JdbcTemplate jdbcTemplate;

    public TestBlogs(BlogRepository blogRepository, JdbcTemplate jdbcTemplate) {
        this.blogRepository = blogRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Blog create(Member owner) {
        String address = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return blogRepository.save(Blog.open(owner, address, "블로그 " + address, false));
    }

    /** 회원의 대표 블로그. 대표는 회원마다 하나라(UNIQUE primary_owner_id) 대표가 없는 회원에게만 쓴다. */
    public Blog createPrimary(Member owner) {
        String address = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return blogRepository.save(Blog.open(owner, address, "블로그 " + address, true));
    }

    public void delete(Blog blog) {
        jdbcTemplate.update("UPDATE blog SET deleted_at = NOW() WHERE id = ?", blog.getId());
    }

    public void restrict(Blog blog) {
        jdbcTemplate.update("UPDATE blog SET is_restricted = TRUE WHERE id = ?", blog.getId());
    }

    public void move(Blog from, Blog to) {
        jdbcTemplate.update("UPDATE blog SET moved_to_blog_id = ? WHERE id = ?", to.getId(), from.getId());
    }

    public void suspendOwner(Blog blog) {
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", blog.getMember().getId());
    }

    /** 블로그 화면이 블로그 API를 부를 때 붙이는 헤더 (스텝 21). 값은 blog.getAddress(). */
    public static final String HEADER = BlogAddressResolver.HEADER;

    /** 블로그 화면의 전체 주소: http://blog.test/@주소/글. path가 "/"면 블로그 첫 화면. */
    public static String url(Blog blog, String path) {
        return "http://blog.test" + BlogAddressResolver.blogPath(blog.getAddress(), path);
    }

}
