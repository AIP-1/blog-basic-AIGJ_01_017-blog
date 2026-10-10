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

    /** 이름이 들어 있는 블로그 태그. 비교는 DB 정렬 규칙대로 대소문자를 구분하지 않는다. */
    List<Tag> findByBlogIdAndNameIn(Long blogId, Collection<String> names);

    Optional<Tag> findByBlogIdAndName(Long blogId, String name);

    List<Tag> findByBlogId(Long blogId);

    /** 태그를 지우기 전에 글과의 연결을 끊는다 (TAG-04). 글은 남는다. 지운 연결 수. */
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM post_tag WHERE tag_id = :tagId", nativeQuery = true)
    int unlinkPosts(@Param("tagId") Long tagId);

    /**
     * 없으면 만든다. 같은 이름(DB 정렬 규칙 기준)이 있거나 다른 트랜잭션이 막 만들었으면 아무것도 하지 않는다(0행).
     * 동시에 같은 새 태그를 만들어도 UNIQUE 위반(500)이 나지 않는다.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO tag (blog_id, name) VALUES (:blogId, :name)", nativeQuery = true)
    int insertIfAbsent(@Param("blogId") Long blogId, @Param("name") String name);

    /**
     * insertIfAbsent 뒤에 읽는 용도. 잠그며 읽어야 다른 트랜잭션이 막 커밋한 태그가 보인다
     * (평범한 SELECT는 이 트랜잭션의 옛 스냅샷을 읽는다, 학습 문서 33).
     * 공유 잠금(FOR SHARE)이다. INSERT IGNORE가 중복을 만나면 그 행에 이미 공유 잠금을 걸어 두는데,
     * 여러 트랜잭션이 거기서 배타 잠금(FOR UPDATE)으로 올리려 하면 서로 기다려 데드락이 난다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from Tag t where t.blog.id = :blogId and t.name = :name")
    Optional<Tag> findLockedByBlogIdAndName(@Param("blogId") Long blogId, @Param("name") String name);

}
