package com.nhnacademy.blog.blog.presentation.dto;

import com.nhnacademy.blog.blog.application.MyBlog;
import com.nhnacademy.blog.blog.domain.Blog;

/**
 * 마이페이지 내 블로그 한 줄 { id, address, name, isPrimary, movedTo, postCount } (GET /api/me/blogs, BLOG-08).
 * movedTo는 이사한 블로그면 새 블로그 주소, 아니면 null.
 */
public record MyBlogResponse(Long id, String address, String name, boolean isPrimary, String movedTo,
                             long postCount) {

    public static MyBlogResponse from(MyBlog myBlog) {
        Blog blog = myBlog.blog();
        return new MyBlogResponse(blog.getId(), blog.getAddress(), blog.getName(), blog.isPrimary(),
                blog.isMoved() ? blog.getMovedToBlog().getAddress() : null, myBlog.postCount());
    }

}
