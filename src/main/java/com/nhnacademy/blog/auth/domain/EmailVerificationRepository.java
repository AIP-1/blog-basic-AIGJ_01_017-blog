package com.nhnacademy.blog.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** 그 이메일로 가장 최근에 보낸 코드. 인덱스 (email, created_at DESC)를 탄다. */
    Optional<EmailVerification> findFirstByEmailOrderByCreatedAtDescIdDesc(String email);

}
