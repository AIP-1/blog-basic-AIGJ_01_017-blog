package com.nhnacademy.blog.member.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 이메일 가입 회원 중에 같은 이메일이 있는가. 소셜 가입·탈퇴 회원은 email이 NULL이라 걸리지 않는다. */
    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);

}
