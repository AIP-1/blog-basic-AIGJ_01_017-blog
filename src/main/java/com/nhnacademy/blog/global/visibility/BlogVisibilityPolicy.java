package com.nhnacademy.blog.global.visibility;

import com.nhnacademy.blog.blog.domain.Blog;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 블로그를 볼 수 있는가 (COM-01, ADMIN-02, ADMIN-05). 볼 수 없으면 존재를 숨기고 404로 처리한다.
 * 삭제된 블로그는 아무도 못 본다. 이용 제한 블로그와 주인이 정지된 블로그는 주인만 본다.
 */
@Component
public class BlogVisibilityPolicy {

    private final Clock clock;

    public BlogVisibilityPolicy(Clock clock) {
        this.clock = clock;
    }

    public boolean canView(Blog blog, Long viewerId) {
        if (blog.isDeleted()) {
            return false;
        }
        if (blog.isOwnedBy(viewerId)) {
            return true;
        }
        return isOpenToOthers(blog);
    }

    /** 주인이 아닌 사람에게 열려 있는가: 이용 제한이 아니고 주인이 정지 중이 아님. */
    public boolean isOpenToOthers(Blog blog) {
        return !blog.isDeleted()
                && !blog.isRestricted()
                && !blog.getMember().isSuspendedAt(LocalDateTime.now(clock));
    }

}
