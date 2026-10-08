package com.nhnacademy.blog.home.application;

import com.nhnacademy.blog.post.domain.Post;
import java.time.LocalDateTime;
import java.util.List;

/** 홈 인기 글 (HOME-02). posts는 1위부터 순서대로이고 블로그·카테고리를 함께 읽어 두었다. */
public record PopularPosts(LocalDateTime snapshotAt, List<Post> posts) {
}
