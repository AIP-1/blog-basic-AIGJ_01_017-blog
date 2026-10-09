package com.nhnacademy.blog.member.application;

import com.nhnacademy.blog.auth.domain.PasswordRule;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.global.auth.AttemptLimiter;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.image.domain.Image;
import com.nhnacademy.blog.image.domain.ImageRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 (GET /api/me)와 회원정보 수정 (T055, AUTH-05). 프론트는 로그인 상태와 대표 블로그를 이것으로 안다.
 */
@Service
public class MeService {

    private final MemberRepository memberRepository;
    private final BlogRepository blogRepository;
    private final ImageRepository imageRepository;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter attemptLimiter;

    public MeService(MemberRepository memberRepository, BlogRepository blogRepository,
                     ImageRepository imageRepository, PasswordEncoder passwordEncoder,
                     AttemptLimiter attemptLimiter) {
        this.memberRepository = memberRepository;
        this.blogRepository = blogRepository;
        this.imageRepository = imageRepository;
        this.passwordEncoder = passwordEncoder;
        this.attemptLimiter = attemptLimiter;
    }

    @Transactional(readOnly = true)
    public MeResult me(Long memberId) {
        Member member = member(memberId);
        String profileImageUrl = member.getProfileImageId() == null ? null
                : imageRepository.findById(member.getProfileImageId()).map(Image::getThumbnailPath).orElse(null);
        return new MeResult(member, blogRepository.findPrimaryByMemberId(memberId).orElse(null), profileImageUrl);
    }

    /**
     * 닉네임·프로필 사진 바꾸기. null인 칸은 그대로 둔다.
     * 닉네임은 다른 회원과 겹치면 409 NICKNAME_TAKEN(대소문자만 바꾸는 것은 된다).
     * 프로필 사진은 본인이 올린 이미지(POST /api/images)만 된다. 남의 이미지 번호면 400.
     */
    @Transactional
    public void update(Long memberId, String rawNickname, Long profileImageId) {
        Member member = member(memberId);
        if (rawNickname != null) {
            String nickname = rawNickname.trim();
            if (nickname.isEmpty()) {
                throw BusinessException.invalidField("nickname", "닉네임을 입력해 주세요.");
            }
            if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
                throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
            }
            member.changeNickname(nickname);
        }
        if (profileImageId != null) {
            imageRepository.findById(profileImageId)
                    .filter(image -> memberId.equals(image.getUploaderId()))
                    .orElseThrow(() -> BusinessException.invalidField("profileImageId", "이미지를 찾을 수 없습니다."));
            member.changeProfileImage(profileImageId);
        }
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 회원이 같은 닉네임을 가져갔다 (UNIQUE uk_member_nickname)
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }
    }

    /**
     * 비밀번호 바꾸기. 이메일 가입 회원만 된다(소셜 가입은 비밀번호가 없어 403).
     * 지금 비밀번호가 틀리면 400(currentPassword), 새 비밀번호는 가입과 같은 규칙(PasswordRule)이다.
     * 지금 비밀번호를 15분 안에 5번 틀리면 15분 동안 429다.
     */
    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = member(memberId);
        if (member.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // 지금 비밀번호를 15분 안에 5번 틀리면 15분 동안 429 (T055a, R-17)
        attemptLimiter.attempt("password-change:" + memberId, () -> {
            if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
                throw BusinessException.invalidField("currentPassword", "지금 비밀번호가 맞지 않습니다.");
            }
            return null;
        }, e -> true);
        PasswordRule.check("newPassword", newPassword);
        member.changePassword(passwordEncoder.encode(newPassword));
    }

    private Member member(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

}
