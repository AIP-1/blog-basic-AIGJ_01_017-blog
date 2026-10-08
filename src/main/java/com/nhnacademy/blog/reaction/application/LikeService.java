package com.nhnacademy.blog.reaction.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import com.nhnacademy.blog.reaction.domain.PostLikeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공감 (T046, SOC-01). 켜기(PUT)·끄기(DELETE)는 몇 번 와도 결과가 같다(멱등, research R-09).
 * 실제로 넣거나 지운 행이 있을 때만 글의 공감 수를 같은 트랜잭션에서 고친다. 그래서 연타해도 수가 어긋나지 않는다.
 */
@Service
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final PostReadService postReadService;

    public LikeService(PostLikeRepository postLikeRepository, PostRepository postRepository,
                       PostReadService postReadService) {
        this.postLikeRepository = postLikeRepository;
        this.postRepository = postRepository;
        this.postReadService = postReadService;
    }

    @Transactional
    public LikeResult like(Blog blog, Long postId, LoginMember member) {
        Post post = likable(blog, postId, member);
        if (postLikeRepository.insertIfAbsent(post.getId(), member.id()) == 1) {
            postRepository.addLikeCount(post.getId(), 1);
        }
        return new LikeResult(true, currentCount(post.getId()));
    }

    @Transactional
    public LikeResult unlike(Blog blog, Long postId, LoginMember member) {
        Post post = likable(blog, postId, member);
        if (postLikeRepository.deleteIfPresent(post.getId(), member.id()) == 1) {
            postRepository.addLikeCount(post.getId(), -1);
        }
        return new LikeResult(false, currentCount(post.getId()));
    }

    /** 글 상세의 viewer.liked. 비회원은 false. */
    @Transactional(readOnly = true)
    public boolean likes(Long postId, Long viewerId) {
        return viewerId != null && postLikeRepository.existsByMemberIdAndPostId(viewerId, postId);
    }

    /** 볼 수 있는 글(404, 구독자 공개 403)인지 먼저, 그다음 회원인지(401). */
    private Post likable(Blog blog, Long postId, LoginMember member) {
        Post post = postReadService.readable(blog, postId, member == null ? null : member.id());
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return post;
    }

    private int currentCount(Long postId) {
        return postRepository.findLikeCount(postId);
    }

}
