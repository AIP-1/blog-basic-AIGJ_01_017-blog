package com.nhnacademy.blog.member.application;

import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 (GET /api/me). 프론트는 로그인 상태와 대표 블로그를 이것으로 안다.
 */
@Service
public class MeService {

    private final MemberRepository memberRepository;
    private final BlogRepository blogRepository;

    public MeService(MemberRepository memberRepository, BlogRepository blogRepository) {
        this.memberRepository = memberRepository;
        this.blogRepository = blogRepository;
    }

    @Transactional(readOnly = true)
    public MeResult me(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new MeResult(member, blogRepository.findPrimaryByMemberId(memberId).orElse(null));
    }

}
