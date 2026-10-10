package com.nhnacademy.blog.admin.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 홈 상단의 최신 공지 1개 (ADMIN-06). */
    Optional<Notice> findFirstByOrderByCreatedAtDescIdDesc();

}
