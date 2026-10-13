package com.nhnacademy.blog.manage.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogBannedWordRepository extends JpaRepository<BlogBannedWord, Long> {

    /** 더하기. 같은 낱말(대소문자 무시)이 있으면 0. */
    @Modifying
    @Query(value = "INSERT INTO blog_banned_word (blog_id, word) VALUES (:blogId, :word) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("blogId") Long blogId, @Param("word") String word);

    List<BlogBannedWord> findByBlogIdOrderByIdAsc(Long blogId);

    long countByBlogId(Long blogId);

}
