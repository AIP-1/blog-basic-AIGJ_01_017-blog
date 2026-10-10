package com.nhnacademy.blog.comment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GuestbookRepository extends JpaRepository<Guestbook, Long>, JpaSpecificationExecutor<Guestbook> {
}
