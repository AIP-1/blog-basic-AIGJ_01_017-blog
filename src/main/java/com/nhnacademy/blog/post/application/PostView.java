package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.post.domain.Post;
import java.util.List;
import java.util.Map;

/**
 * 글 상세에 필요한 것 (GET /api/posts/{id}, POST-04). post는 블로그·주인·카테고리가 읽혀 있다.
 *
 * @param authorPrimaryBlogAddress 글쓴이(블로그 주인)의 대표 블로그 주소. 없거나 볼 수 없으면 null
 * @param blind                    주인이 숨긴 글을 볼 때만 { reason, reasonMessage }, 아니면 null
 * @param prev                     같은 블로그에서 바로 전에 발행한, 보는 사람이 볼 수 있는 글. 없으면 null
 * @param next                     바로 다음에 발행한 글. 없으면 null
 */
public record PostView(Post post, List<String> tagNames, boolean owner, String authorPrimaryBlogAddress,
                       Map<String, String> blind, Neighbor prev, Neighbor next) {

    public record Neighbor(Long id, String title) {
    }

}
