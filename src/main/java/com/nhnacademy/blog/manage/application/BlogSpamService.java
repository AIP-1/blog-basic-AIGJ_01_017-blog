package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.manage.domain.BlogBannedWord;
import com.nhnacademy.blog.manage.domain.BlogBannedWordRepository;
import com.nhnacademy.blog.manage.domain.BlogBlockedMember;
import com.nhnacademy.blog.manage.domain.BlogBlockedMemberRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.subscription.domain.SubscriptionRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 주인의 차단 회원·금칙어 관리 (T084, MNG-04). 주인 검사는 컨트롤러가 먼저 했다.
 */
@Service
public class BlogSpamService {

    public static final int PAGE_SIZE = 20;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final BlogBlockedMemberRepository blockedMemberRepository;
    private final BlogBannedWordRepository bannedWordRepository;
    private final MemberRepository memberRepository;
    private final SubscriptionRepository subscriptionRepository;

    public BlogSpamService(BlogBlockedMemberRepository blockedMemberRepository,
                           BlogBannedWordRepository bannedWordRepository, MemberRepository memberRepository,
                           SubscriptionRepository subscriptionRepository) {
        this.blockedMemberRepository = blockedMemberRepository;
        this.bannedWordRepository = bannedWordRepository;
        this.memberRepository = memberRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public Page<BlogBlockedMember> blocked(Blog blog, PageQuery page) {
        return blockedMemberRepository.findByBlogId(blog.getId(), page.toPageable(NEWEST));
    }

    /**
     * 차단. 없거나 탈퇴한 회원 404, 자기 자신 400(memberId), 이미 차단 409 ALREADY_BLOCKED.
     * 그 회원의 이 블로그 구독은 지운다(차단 사실은 따로 알리지 않음).
     */
    @Transactional
    public void block(Blog blog, Long memberId, String rawMemo) {
        if (memberId == null) {
            throw BusinessException.invalidField("memberId", "차단할 회원을 골라 주세요.");
        }
        Member member = memberRepository.findById(memberId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (blog.isOwnedBy(member.getId())) {
            throw BusinessException.invalidField("memberId", "자기 자신은 차단할 수 없습니다.");
        }
        String memo = rawMemo == null || rawMemo.isBlank() ? null : rawMemo.strip();
        if (memo != null && memo.length() > BlogBlockedMember.MEMO_LENGTH) {
            throw BusinessException.invalidField("memo", "메모는 " + BlogBlockedMember.MEMO_LENGTH + "자까지입니다.");
        }
        if (blockedMemberRepository.insertIfAbsent(blog.getId(), memberId, memo) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
        subscriptionRepository.deleteIfPresent(memberId, blog.getId());
    }

    /** 차단 해제. 차단하지 않았던 회원이어도 204(멱등). */
    @Transactional
    public void unblock(Blog blog, Long memberId) {
        blockedMemberRepository.deleteIfPresent(blog.getId(), memberId);
    }

    @Transactional(readOnly = true)
    public List<BlogBannedWord> bannedWords(Blog blog) {
        return bannedWordRepository.findByBlogIdOrderByIdAsc(blog.getId());
    }

    /** 금칙어 더하기. 1~30자(앞뒤 공백 제거, 400 word), 100개 넘으면 409 BANNED_WORD_LIMIT, 같은 낱말 409 NAME_TAKEN. */
    @Transactional
    public BlogBannedWord addBannedWord(Blog blog, String rawWord) {
        String word = rawWord == null ? "" : rawWord.strip();
        if (word.isEmpty() || word.length() > BlogBannedWord.WORD_LENGTH) {
            throw BusinessException.invalidField("word", "금칙어는 1~" + BlogBannedWord.WORD_LENGTH + "자입니다.");
        }
        if (bannedWordRepository.countByBlogId(blog.getId()) >= BlogBannedWord.MAX_PER_BLOG) {
            throw new BusinessException(ErrorCode.BANNED_WORD_LIMIT);
        }
        if (bannedWordRepository.insertIfAbsent(blog.getId(), word) == 0) {
            throw new BusinessException(ErrorCode.NAME_TAKEN);
        }
        return bannedWordRepository.findByBlogIdOrderByIdAsc(blog.getId()).stream()
                .filter(found -> found.getWord().equalsIgnoreCase(word))
                .findFirst()
                .orElseThrow();
    }

    /** 금칙어 지우기. 이 블로그 것이 아니거나 없으면 404. */
    @Transactional
    public void deleteBannedWord(Blog blog, Long id) {
        BlogBannedWord word = bannedWordRepository.findById(id)
                .filter(found -> found.getBlogId().equals(blog.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        bannedWordRepository.delete(word);
    }

}
