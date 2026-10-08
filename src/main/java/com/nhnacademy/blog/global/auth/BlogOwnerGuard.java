package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 블로그 주인만 하는 일(관리, 글쓰기 등)의 주인 검사 (T050, COM-01).
 * 블로그가 있는지(404)는 먼저 확인했다고 보고, 비회원 401, 주인이 아니면 403 순서로 판단한다.
 */
@Component
public class BlogOwnerGuard {

    public LoginMember requireOwner(Blog blog, LoginMember member) {
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!blog.isOwnedBy(member.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return member;
    }

}
