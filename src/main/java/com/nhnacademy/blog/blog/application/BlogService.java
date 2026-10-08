package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogAddressRule;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.FieldErrorDetail;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 개설과 정보 수정 (T022, T023, BLOG-01, BLOG-02).
 */
@Service
public class BlogService {

    private final BlogRepository blogRepository;
    private final MemberRepository memberRepository;

    public BlogService(BlogRepository blogRepository, MemberRepository memberRepository) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
    }

    /** 개설 화면에서 주소를 입력할 때 미리 확인한다. 실제 개설에서도 같은 순서로 다시 본다. */
    @Transactional(readOnly = true)
    public AddressCheck checkAddress(String address) {
        if (!BlogAddressRule.hasValidFormat(address)) {
            return AddressCheck.unavailable(AddressCheck.Reason.INVALID);
        }
        if (BlogAddressRule.isReserved(address)) {
            return AddressCheck.unavailable(AddressCheck.Reason.RESERVED);
        }
        if (blogRepository.existsByAddress(address)) {
            return AddressCheck.unavailable(AddressCheck.Reason.TAKEN);
        }
        return AddressCheck.ok();
    }

    /**
     * 블로그 개설 (BLOG-01). 주소 규칙 400 → 활성 5개 한도 409 → 주소 중복 409 순서로 본다.
     * 회원 행을 잠가서 같은 회원이 동시에 두 번 개설해도 한도와 대표 블로그가 어긋나지 않는다.
     * 활성 블로그가 하나도 없으면 새 블로그가 대표 블로그다.
     * 돌려주는 블로그의 주인(member)은 이 트랜잭션에서 읽은 엔티티라 트랜잭션 밖에서도 읽을 수 있다.
     */
    @Transactional
    public Blog open(Long memberId, String address, String name, String description) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        checkAddressRule(address);
        long activeCount = blogRepository.countActiveByMemberId(memberId);
        if (activeCount >= Blog.MAX_ACTIVE_PER_MEMBER) {
            throw new BusinessException(ErrorCode.BLOG_LIMIT_EXCEEDED);
        }
        if (blogRepository.existsByAddress(address)) {
            throw new BusinessException(ErrorCode.BLOG_ADDRESS_TAKEN);
        }
        try {
            return blogRepository.saveAndFlush(
                    Blog.open(member, address, name.trim(), blankToNull(description), activeCount == 0));
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 회원이 같은 주소로 먼저 개설했다
            throw new BusinessException(ErrorCode.BLOG_ADDRESS_TAKEN);
        }
    }

    /**
     * 이름·소개 수정 (BLOG-02). 주인 검사는 컨트롤러가 먼저 했다.
     * 응답에 주인 정보가 필요해 주인을 함께 읽는 findByAddress로 다시 읽는다.
     */
    @Transactional
    public Blog updateInfo(String address, String name, String description) {
        Blog blog = blogRepository.findByAddress(address)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        blog.changeInfo(name == null ? null : name.trim(), description);
        return blog;
    }

    private void checkAddressRule(String address) {
        String reason = null;
        if (!BlogAddressRule.hasValidFormat(address)) {
            reason = "영문 소문자·숫자·하이픈 4~32자로, 하이픈으로 시작하거나 끝날 수 없습니다.";
        } else if (BlogAddressRule.isReserved(address)) {
            reason = "쓸 수 없는 주소입니다.";
        }
        if (reason != null) {
            throw BusinessException.fieldErrors(ErrorCode.BLOG_ADDRESS_INVALID,
                    List.of(new FieldErrorDetail("address", reason)));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

}
