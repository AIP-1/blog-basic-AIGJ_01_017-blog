package com.nhnacademy.blog.global.visibility;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.post.domain.Post;

/**
 * 글 하나를 볼 수 있는지 판단한 결과 (data-model.md 글 가시성 판단의 결과 6개).
 */
public sealed interface PostAccess {

    /** 없거나 볼 수 없다. 존재를 숨기므로 로그인 여부와 상관없이 404다. */
    record NotFound() implements PostAccess {
    }

    /** 요청한 블로그가 아니라 다른 블로그 소속이고 볼 수 있다. 지금 소속 블로그로 301. */
    record MovedTo(Blog blog) implements PostAccess {
    }

    /** 블로그 주인에게 보인다. 숨긴 글이면 blinded가 true이고 숨김 사유를 함께 보여 준다. */
    record Owner(Post post, boolean blinded) implements PostAccess {
    }

    /** 주인이 아닌 사람에게 보인다. */
    record Visible(Post post) implements PostAccess {
    }

    /** 구독자 공개 글인데 구독하지 않았다. 제목·본문 없이 구독 안내만 (403 SUBSCRIBERS_ONLY, Q4). */
    record SubscribersOnly(Blog blog) implements PostAccess {
    }

    default boolean canRead() {
        return this instanceof Owner || this instanceof Visible;
    }

}
