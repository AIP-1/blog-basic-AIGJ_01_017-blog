package com.nhnacademy.blog.member.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 이메일 가입 회원 중에 같은 이메일이 있는가. 소셜 가입·탈퇴 회원은 email이 NULL이라 걸리지 않는다. */
    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /** 나 말고 이 닉네임을 쓰는 회원이 있나. 대소문자는 DB 정렬 규칙대로 같게 본다. */
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<Member> findByEmail(String email);

    /**
     * 회원 행을 잠그고 읽는다(SELECT ... FOR UPDATE). 같은 회원의 요청을 한 줄로 세울 때 쓴다.
     * 예: 블로그 개설에서 동시에 두 번 와도 5개 한도와 대표 블로그를 한 번씩 차례로 판단한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

}
