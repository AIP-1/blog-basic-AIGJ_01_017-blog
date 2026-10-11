package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.manage.domain.BlogBannedWord;
import com.nhnacademy.blog.manage.domain.BlogBannedWordRepository;
import com.nhnacademy.blog.manage.domain.BlogBlockedMemberRepository;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단·금칙어 검사 (T084, MNG-04). 댓글·방명록 쓰기와 구독이 부른다. 차단 사실은 따로 알리지 않고 "이 블로그에서 차단되었습니다"(403)만,
 * 금칙어는 400 BANNED_WORD다. 금칙어는 새로 쓰는 글에만 본다(이미 있던 댓글은 그대로, 명세 MNG-04).
 */
@Component
public class SpamGuard {

    private final BlogBlockedMemberRepository blockedMemberRepository;
    private final BlogBannedWordRepository bannedWordRepository;

    public SpamGuard(BlogBlockedMemberRepository blockedMemberRepository, BlogBannedWordRepository bannedWordRepository) {
        this.blockedMemberRepository = blockedMemberRepository;
        this.bannedWordRepository = bannedWordRepository;
    }

    /** 이 블로그에서 차단된 회원이면 403 BLOCKED_BY_BLOG. */
    @Transactional(readOnly = true)
    public void requireNotBlocked(Blog blog, Long memberId) {
        if (memberId != null && blockedMemberRepository.existsByBlogIdAndBlockedMemberId(blog.getId(), memberId)) {
            throw new BusinessException(ErrorCode.BLOCKED_BY_BLOG);
        }
    }

    /** 내용에 이 블로그의 금칙어가 들어 있으면(대소문자 무시) 400 BANNED_WORD. */
    @Transactional(readOnly = true)
    public void requireNoBannedWord(Blog blog, String content) {
        if (content == null) {
            return;
        }
        String text = content.toLowerCase(Locale.ROOT);
        for (BlogBannedWord word : bannedWordRepository.findByBlogIdOrderByIdAsc(blog.getId())) {
            if (text.contains(word.getWord().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.BANNED_WORD);
            }
        }
    }

}
