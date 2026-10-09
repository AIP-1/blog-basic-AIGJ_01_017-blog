package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.post.domain.Post;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * 내 글 관리 한 페이지. blindReasons는 관리자가 숨긴 글의 사유(글 id → { reason, reasonMessage })이고,
 * 숨긴 글만 들어 있다(최신 BLIND 관리 이력, ADMIN-03).
 */
public record ManagedPostPage(Page<Post> posts, Map<Long, Map<String, String>> blindReasons) {
}
