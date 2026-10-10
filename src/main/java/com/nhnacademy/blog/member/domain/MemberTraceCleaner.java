package com.nhnacademy.blog.member.domain;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴한 회원의 흔적 정리 (AUTH-06). 그 회원이 누른 공감과 구독을 지우고 수치(공감 수)를 맞추며, 그 회원이 쓴 댓글·방명록을
 * 소프트 삭제하고 댓글 수를 맞추고, 소셜 연동을 끊는다. 답글이 달린 댓글은 화면에서 "삭제된 댓글입니다"로 자리만 남는다(CMT-05).
 * 작성자가 고친 것이 아니라서 글·댓글의 수정 시각은 그대로 둔다. 같은 트랜잭션 안에서 부른다.
 */
@Repository
public class MemberTraceCleaner {

    private final JdbcTemplate jdbcTemplate;

    public MemberTraceCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean(Long memberId, LocalDateTime now) {
        // 공감: 글의 공감 수를 먼저 하나씩 줄이고 지운다
        jdbcTemplate.update("""
                UPDATE post p JOIN post_like l ON l.post_id = p.id
                SET p.like_count = p.like_count - 1, p.updated_at = p.updated_at
                WHERE l.member_id = ?""", memberId);
        jdbcTemplate.update("DELETE FROM post_like WHERE member_id = ?", memberId);
        // 구독: 구독자 수는 행을 세므로 지우기만 하면 맞는다
        jdbcTemplate.update("DELETE FROM subscription WHERE member_id = ?", memberId);
        // 댓글: 글마다 지울 댓글 수만큼 댓글 수를 줄이고 소프트 삭제
        jdbcTemplate.update("""
                UPDATE post p JOIN (SELECT post_id, COUNT(*) AS n FROM comment
                                    WHERE member_id = ? AND deleted_at IS NULL GROUP BY post_id) c ON c.post_id = p.id
                SET p.comment_count = p.comment_count - c.n, p.updated_at = p.updated_at""", memberId);
        jdbcTemplate.update("""
                UPDATE comment SET deleted_at = ?, updated_at = updated_at
                WHERE member_id = ? AND deleted_at IS NULL""", now, memberId);
        jdbcTemplate.update("""
                UPDATE guestbook SET deleted_at = ?, updated_at = updated_at
                WHERE member_id = ? AND deleted_at IS NULL""", now, memberId);
        // 소셜 연동을 끊어 같은 소셜 계정을 다른 회원에 다시 연결할 수 있게
        jdbcTemplate.update("DELETE FROM social_account WHERE member_id = ?", memberId);
    }

}
