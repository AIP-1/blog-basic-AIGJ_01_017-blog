package com.nhnacademy.blog.post.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.visibility.PostAccess;
import com.nhnacademy.blog.global.visibility.PostVisibilityPolicy;
import com.nhnacademy.blog.image.application.PostThumbnails;
import com.nhnacademy.blog.post.domain.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 미리보기 (T072). 미리보기를 가져가는 메신저·SNS 서버는 로그인하지 않고 오므로,
 * <b>비회원이 볼 수 있는 글</b>만 미리보기를 만든다. 비공개·구독자 공개·숨긴 글은 빈 값이라
 * 제목이나 요약이 링크 미리보기로 새지 않는다(contracts 화면 주소 단계 "볼 수 없는 글이면 넣지 않는다").
 */
@Service
public class PostPreviewService {

    private final PostVisibilityPolicy postVisibilityPolicy;
    private final PostThumbnails postThumbnails;

    public PostPreviewService(PostVisibilityPolicy postVisibilityPolicy, PostThumbnails postThumbnails) {
        this.postVisibilityPolicy = postVisibilityPolicy;
        this.postThumbnails = postThumbnails;
    }

    @Transactional(readOnly = true)
    public Optional<PostPreview> preview(Blog blog, Long postId) {
        if (!(postVisibilityPolicy.decide(postId, blog, null) instanceof PostAccess.Visible visible)) {
            return Optional.empty();
        }
        Post post = visible.post();
        String image = postThumbnails.of(List.of(post)).get(post.getId());
        return Optional.of(new PostPreview(post.getId(), post.getTitle(), post.getSummary(), image, blog.getName()));
    }

}
