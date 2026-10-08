package com.nhnacademy.blog.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.nhnacademy.blog.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Flyway V2가 관리자 초기 계정을 넣는지 확인한다 (ADMIN-01).
 */
class AdminAccountMigrationTest extends IntegrationTestSupport {

    @Autowired
    MemberRepository memberRepository;

    @Test
    void adminAccountExists() {
        Member admin = memberRepository.findAll().stream()
                .filter(member -> "admin@blog.test".equals(member.getEmail()))
                .findFirst()
                .orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(admin.getNickname()).isEqualTo("admin");
        assertThat(new BCryptPasswordEncoder().matches("admin1234!", admin.getPasswordHash())).isTrue();
    }

}
