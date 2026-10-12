package com.nhnacademy.blog.bookmark.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.bookmark.domain.PostBookmark;
import com.nhnacademy.blog.bookmark.domain.PostBookmarkRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.PostAccess;
import com.nhnacademy.blog.global.visibility.PostVisibilityPolicy;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.post.application.PostReadService;
import com.nhnacademy.blog.post.domain.Post;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장한 글 (T074·T075, SOC-03). 저장·취소는 멱등한 PUT·DELETE(연달아 눌러도 한 번)이고, 목록은 본인만 저장 최신순 20개씩 더보기다.
 * 목록의 각 글은 지금 볼 수 있는지 글 상세와 같은 판단(PostVisibilityPolicy)으로 다시 본다. 볼 수 없으면 저장할 때의 제목·블로그 이름만
 * 주고, 왜 볼 수 없는지(삭제·비공개·구독 해제 …)는 알리지 않는다.
 */
@Service
public class BookmarkService {

    public static final int PAGE_SIZE = 20;

    private final PostBookmarkRepository bookmarkRepository;
    private final PostReadService postReadService;
    private final PostVisibilityPolicy postVisibilityPolicy;

    public BookmarkService(PostBookmarkRepository bookmarkRepository, PostReadService postReadService,
                           PostVisibilityPolicy postVisibilityPolicy) {
        this.bookmarkRepository = bookmarkRepository;
        this.postReadService = postReadService;
        this.postVisibilityPolicy = postVisibilityPolicy;
    }

    /** 글 상세의 저장. 볼 수 있는 글(404, 구독자 공개 403)인지 먼저, 그다음 회원인지(401). */
    @Transactional
    public void save(Blog blog, Long postId, LoginMember member) {
        Post post = savable(blog, postId, member);
        bookmarkRepository.insertIfAbsent(post.getId(), member.id(), post.getTitle(), post.getBlog().getName());
    }

    /** 글 상세의 저장 취소. 같은 순서로 확인한 뒤 지운다(없었어도 204). */
    @Transactional
    public void unsave(Blog blog, Long postId, LoginMember member) {
        Post post = savable(blog, postId, member);
        bookmarkRepository.deleteIfPresent(member.id(), post.getId());
    }

    /** 저장한 글 목록에서 바로 취소. 볼 수 없게 된 글도 지울 수 있어야 해서 글을 확인하지 않는다. 남의 저장은 건드릴 수 없다. */
    @Transactional
    public void remove(Long memberId, Long postId) {
        bookmarkRepository.deleteIfPresent(memberId, postId);
    }

    /** 글 상세의 viewer.bookmarked. 비회원은 false. */
    @Transactional(readOnly = true)
    public boolean bookmarked(Long postId, Long viewerId) {
        return viewerId != null && bookmarkRepository.existsByMemberIdAndPostId(viewerId, postId);
    }

    /** 저장 최신순 20개(+1개 더 읽어 다음 커서를 정한다). 각 항목은 지금 볼 수 있으면 글, 아니면 저장할 때의 이름만. */
    @Transactional(readOnly = true)
    public List<SavedItem> list(Long memberId, TimeIdCursor cursor) {
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<PostBookmark> bookmarks = cursor == null
                ? bookmarkRepository.findLatest(memberId, limit)
                : bookmarkRepository.findLatestAfter(memberId, cursor.time(), cursor.id(), limit);
        List<SavedItem> items = new ArrayList<>();
        for (PostBookmark bookmark : bookmarks) {
            Post post = switch (postVisibilityPolicy.decide(bookmark.getPostId(), null, memberId)) {
                case PostAccess.Owner owner -> owner.blinded() ? null : owner.post();
                case PostAccess.Visible visible -> visible.post();
                default -> null;
            };
            items.add(new SavedItem(bookmark, post));
        }
        return items;
    }

    private Post savable(Blog blog, Long postId, LoginMember member) {
        Post post = postReadService.readable(blog, postId, member == null ? null : member.id());
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return post;
    }

    /** post가 null이면 지금은 볼 수 없는 글이다. */
    public record SavedItem(PostBookmark bookmark, Post post) {
    }

}
