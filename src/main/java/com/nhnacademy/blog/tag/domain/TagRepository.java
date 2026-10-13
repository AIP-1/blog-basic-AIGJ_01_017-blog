package com.nhnacademy.blog.tag.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByBlogIdAndName(Long blogId, String name);

    List<Tag> findByBlogId(Long blogId);

    /** 이름이 들어 있는 블로그 태그. 비교는 대소문자를 구분하지 않는다(name은 citext). */
    List<Tag> findByBlogIdAndNameIn(Long blogId, Collection<String> names);

    /** 이 태그를 단 글 번호(지운 글 포함). 태그 이름을 바꾸거나 지울 때 그 글들의 추천 임베딩을 다시 만드는 데 쓴다 (T120). */
    @Query("select pt.post.id from PostTag pt where pt.tag.id = :tagId")
    List<Long> findPostIdsByTagId(@Param("tagId") Long tagId);

    /**
     * 이미 있는 태그를 번호로 공유 잠금(FOR SHARE)하며 읽는다. 잠그며 읽으면 다른 트랜잭션이 막 지운 태그는 나오지 않고
     * (그러면 새로 만든다), 이 트랜잭션이 끝날 때까지 그 태그를 지우는 쪽이 기다린다. 잠그지 않으면 읽은 뒤 지워진 태그에
     * 글을 연결하다 외래 키 오류(500)가 난다.
     * 이름이 아니라 번호(기본 키)로 잠그는 이유: MySQL에서는 이름으로 잠그면 아직 없는 이름의 "빈 자리"(gap)까지 잠겨
     * 데드락이 났다. PostgreSQL에는 빈 자리 잠금이 없지만, 있는 행만 정확히 잠그는 지금 방식을 그대로 둔다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from Tag t where t.id in :ids")
    List<Tag> findLockedByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * 주어진 태그 가운데 지우지 않은 글이 하나도 없는 것을 지운다 (2026-10-11 지원 결정: 글이 없는 태그는 없는 태그).
     * 지운 글의 연결(post_tag)은 외래 키의 ON DELETE CASCADE로 같이 지워진다.
     * flushAutomatically: 이 트랜잭션에서 바꾼 연결·삭제 시각을 먼저 DB에 보내야 "남은 글"을 바르게 센다.
     * 영속성 컨텍스트는 비우지 않는다(부른 쪽이 글을 계속 쓴다). 지운 태그를 가리키는 연결은 이 트랜잭션에서 더 바꾸지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM tag
            WHERE id IN (:tagIds)
              AND NOT EXISTS (SELECT 1 FROM post_tag pt JOIN post p ON p.id = pt.post_id
                              WHERE pt.tag_id = tag.id AND p.deleted_at IS NULL)""", nativeQuery = true)
    int deleteUnused(@Param("tagIds") Collection<Long> tagIds);

    /**
     * 없으면 만든다. 같은 이름(대소문자 무시, citext)이 있거나 다른 트랜잭션이 막 만들었으면 아무것도 하지 않는다(0행).
     * 동시에 같은 새 태그를 만들어도 UNIQUE 위반(500)이 나지 않는다.
     */
    @Modifying
    @Query(value = "INSERT INTO tag (blog_id, name) VALUES (:blogId, :name) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("blogId") Long blogId, @Param("name") String name);

    /**
     * insertIfAbsent 뒤에 읽는 용도. 다른 트랜잭션이 같은 이름을 넣는 중이면 insertIfAbsent(ON CONFLICT DO NOTHING)가
     * 그 트랜잭션이 끝나기를 기다리고, 그 뒤 이 SELECT가 막 커밋된 행을 읽는다(READ COMMITTED는 문장마다 새로 본다).
     * 공유 잠금(FOR SHARE)으로 읽어, 이 트랜잭션이 끝날 때까지 그 태그를 지우는 쪽이 기다린다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from Tag t where t.blog.id = :blogId and t.name = :name")
    Optional<Tag> findLockedByBlogIdAndName(@Param("blogId") Long blogId, @Param("name") String name);

}
