package com.nhnacademy.blog.tag.presentation;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.tag.application.TagListService;
import com.nhnacademy.blog.tag.presentation.dto.TagCountResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 태그 목록 (T054, TAG-03). 블로그 주소에서 부른다. 태그 이름 바꾸기·지우기(TAG-04)는 백로그다.
 */
@RestController
public class TagController {

    private final TagListService tagListService;

    public TagController(TagListService tagListService) {
        this.tagListService = tagListService;
    }

    @GetMapping("/api/tags")
    public List<TagCountResponse> tags(@CurrentBlog Blog blog) {
        return tagListService.tags(blog, LoginMembers.currentId()).stream().map(TagCountResponse::from).toList();
    }

}
