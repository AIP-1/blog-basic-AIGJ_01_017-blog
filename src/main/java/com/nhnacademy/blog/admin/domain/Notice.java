package com.nhnacademy.blog.admin.domain;

import com.nhnacademy.blog.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 공지 (ADMIN-06). 관리자가 쓰고 고치고 지운다. 내용은 HTML이 아니라 글자 그대로다(화면이 줄바꿈만 살려 보여 준다).
 */
@Entity
@Table(name = "notice")
public class Notice extends BaseTimeEntity {

    public static final int TITLE_LENGTH = 200;
    public static final int CONTENT_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    protected Notice() {
    }

    private Notice(Long adminId, String title, String content) {
        this.adminId = adminId;
        this.title = title;
        this.content = content;
    }

    public static Notice write(Long adminId, String title, String content) {
        return new Notice(adminId, title, content);
    }

    public void edit(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

}
