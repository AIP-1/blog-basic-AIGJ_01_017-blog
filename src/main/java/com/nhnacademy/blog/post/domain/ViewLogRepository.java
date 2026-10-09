package com.nhnacademy.blog.post.domain;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewLogRepository extends JpaRepository<ViewLog, Long> {

    /** 이 조회자가 since 뒤에 이 글을 본 기록이 있는가. 인덱스 (post_id, viewer_key, viewed_at)를 탄다. */
    boolean existsByPostIdAndViewerKeyAndViewedAtAfter(Long postId, String viewerKey, LocalDateTime since);

}
