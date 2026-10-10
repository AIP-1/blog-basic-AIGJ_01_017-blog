package com.nhnacademy.blog.manage;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.comment.domain.Guestbook;
import com.nhnacademy.blog.comment.domain.GuestbookRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 받은 댓글·방명록 관리 (T068, MNG-02, spec US8 "받은 댓글에서 답글 바로 쓰기", 스텝 14 "관리 화면에는 내 블로그의 댓글·방명록만").
 */
class ManageCommentIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    TestPosts testPosts;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    GuestbookRepository guestbookRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member visitor;
    Blog blog;
    Post post;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        visitor = testMembers.create();
        blog = testBlogs.create(owner);
        post = testPosts.published(blog, Visibility.PUBLIC);
    }

    @Test
    void ownerSeesReceivedCommentsOfOwnBlogOnlyNewestFirstWithPost() throws Exception {
        Comment first = commentRepository.save(Comment.write(post, visitor, "첫 댓글", false));
        commentRepository.save(Comment.reply(first, owner, "주인 답글", false));
        Comment secret = commentRepository.save(Comment.write(post, visitor, "비밀 댓글", true));
        Comment removed = commentRepository.save(Comment.write(post, visitor, "지운 댓글", false));
        jdbcTemplate.update("UPDATE comment SET deleted_at = NOW() WHERE id = ?", removed.getId());
        Post deletedPost = testPosts.published(blog, Visibility.PUBLIC);
        commentRepository.save(Comment.write(deletedPost, visitor, "지운 글의 댓글", false));
        jdbcTemplate.update("UPDATE post SET deleted_at = NOW() WHERE id = ?", deletedPost.getId());
        // 다른 블로그의 댓글은 보이지 않는다
        Blog otherBlog = testBlogs.create(visitor);
        commentRepository.save(Comment.write(testPosts.published(otherBlog, Visibility.PUBLIC), owner, "남의 블로그", false));

        list(owner, "comment", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                // 주인이라 비밀댓글 내용도 보인다
                .andExpect(jsonPath("$.content[0].content").value("비밀 댓글"))
                .andExpect(jsonPath("$.content[0].secret").value(true))
                .andExpect(jsonPath("$.content[0].state").value("NORMAL"))
                .andExpect(jsonPath("$.content[0].post.id").value(post.getId()))
                .andExpect(jsonPath("$.content[0].post.title").value(post.getTitle()))
                .andExpect(jsonPath("$.content[0].viewer.canDelete").value(true))
                .andExpect(jsonPath("$.content[0].viewer.canEdit").value(false))
                .andExpect(jsonPath("$.content[1].content").value("첫 댓글"))
                .andExpect(jsonPath("$.content[1].author.nickname").value(visitor.getNickname()));
        // type을 빼면 댓글이다
        list(owner, null, null).andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void guestbookTabListsReceivedEntriesIncludingReplies() throws Exception {
        Guestbook entry = guestbookRepository.save(Guestbook.write(blog, visitor, "방명록 글", false));
        guestbookRepository.save(Guestbook.reply(entry, owner, "주인 답", false));
        Member another = testMembers.create();
        guestbookRepository.save(Guestbook.reply(entry, another, "다른 방문자 답", false));

        list(owner, "guestbook", 1)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].content").value("다른 방문자 답"))
                .andExpect(jsonPath("$.content[0].parentId").value(entry.getId()))
                .andExpect(jsonPath("$.content[0].post").doesNotExist())
                .andExpect(jsonPath("$.content[1].content").value("방명록 글"));
    }

    @Test
    void onlyOwnerAndPermissionBeforeInputErrors() throws Exception {
        list(null, "x", 0).andExpect(status().isUnauthorized());
        list(visitor, "x", 0).andExpect(status().isForbidden());
        list(owner, "x", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));
        list(owner, "comment", 0).andExpect(status().isBadRequest());
    }

    private ResultActions list(Member member, String type, Integer page) throws Exception {
        var request = get("/api/manage/comments").header(HttpHeaders.HOST, TestBlogs.host(blog));
        if (type != null) {
            request.param("type", type);
        }
        if (page != null) {
            request.param("page", String.valueOf(page));
        }
        return mockMvc.perform(member == null ? request : request.cookie(testMembers.loginCookies(member)));
    }

}
