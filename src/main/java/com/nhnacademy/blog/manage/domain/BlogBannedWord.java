package com.nhnacademy.blog.manage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 블로그 금칙어 (T084, MNG-04). 블로그마다 100개까지. 같은 블로그에 같은 낱말은 하나(UNIQUE, word가 citext라 대소문자 무시).
 * 새로 쓰는 댓글·방명록에 들어 있으면(대소문자 무시) 400 BANNED_WORD.
 */
@Entity
@Table(name = "blog_banned_word")
public class BlogBannedWord {

    public static final int WORD_LENGTH = 30;
    public static final int MAX_PER_BLOG = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "word", nullable = false, length = WORD_LENGTH, columnDefinition = "citext")
    private String word;

    protected BlogBannedWord() {
    }

    public Long getId() {
        return id;
    }

    public Long getBlogId() {
        return blogId;
    }

    public String getWord() {
        return word;
    }

}
