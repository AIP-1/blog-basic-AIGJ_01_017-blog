package com.nhnacademy.blog.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 댓글 고치기 (T066, CMT-03, spec US7 시나리오 2: 내 댓글만 고쳐진다).
 */
class CommentEditIntegrationTest extends IntegrationTestSupport {

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
    JdbcTemplate jdbcTemplate;

    Member owner;
    Member reader;
    Blog blog;
    Post post;
    Comment mine;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        reader = testMembers.create();
        blog = testBlogs.create(owner);
        post = testPosts.published(blog, Visibility.PUBLIC);
        mine = commentRepository.save(Comment.write(post, reader, "처음 내용", false));
    }

    @Test
    void authorEditsOwnCommentAndSeesEditedTime() throws Exception {
        // 목록에서 작성자에게만 수정 버튼이 보이도록 canEdit을 준다
        list(reader).andExpect(jsonPath("$.content[0].viewer.canEdit").value(true))
                .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());
        list(owner).andExpect(jsonPath("$.content[0].viewer.canEdit").value(false))
                .andExpect(jsonPath("$.content[0].viewer.canDelete").value(true));

        edit(mine, reader, "{\"content\":\"  고친 내용 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고친 내용"))
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.viewer.canEdit").value(true));
        list(null).andExpect(jsonPath("$.content[0].content").value("고친 내용"))
                .andExpect(jsonPath("$.content[0].updatedAt").isString());
        // 댓글을 고친 것은 글을 고친 것이 아니고, 댓글 수도 그대로다
        assertThat(jdbcTemplate.queryForObject("SELECT comment_count FROM post WHERE id = ?", Integer.class,
                post.getId())).isZero();
    }

    @Test
    void onlyAuthorCanEditAndPermissionComesBeforeInputErrors() throws Exception {
        String invalid = "{\"content\":\"" + "가".repeat(1001) + "\"}";

        edit(mine, null, invalid).andExpect(status().isUnauthorized());
        // 블로그 주인도 남의 댓글은 고칠 수 없다(지우기만 된다)
        edit(mine, owner, "{\"content\":\"주인이 고침\"}").andExpect(status().isForbidden());
        edit(mine, reader, invalid).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
        edit(mine, reader, "{\"content\":\"   \"}").andExpect(status().isBadRequest());
        assertThat(commentRepository.findById(mine.getId()).orElseThrow().getContent()).isEqualTo("처음 내용");
    }

    @Test
    void blindedDeletedOrHiddenCommentsCannotBeEdited() throws Exception {
        jdbcTemplate.update("UPDATE comment SET is_blinded = 1 WHERE id = ?", mine.getId());
        // 숨긴 댓글은 작성자에게 보이지만 고칠 수 없다(ADMIN-03)
        list(reader).andExpect(jsonPath("$.content[0].viewer.canEdit").value(false));
        edit(mine, reader, "{\"content\":\"숨김 뒤 고침\"}").andExpect(status().isForbidden());

        Comment deleted = commentRepository.save(Comment.write(post, reader, "지울 댓글", false));
        jdbcTemplate.update("UPDATE comment SET deleted_at = NOW() WHERE id = ?", deleted.getId());
        edit(deleted, reader, "{\"content\":\"되살리기\"}").andExpect(status().isNotFound());

        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);
        Comment onPrivate = commentRepository.save(Comment.write(privatePost, reader, "비공개 글 댓글", false));
        edit(onPrivate, reader, "{\"content\":\"고침\"}").andExpect(status().isNotFound());
    }

    private ResultActions edit(Comment target, Member member, String body) throws Exception {
        var request = patch("/api/comments/" + target.getId())
                .header(HttpHeaders.HOST, TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return mockMvc.perform(member == null ? request : request.cookie(testMembers.loginCookies(member)));
    }

    private ResultActions list(Member member) throws Exception {
        var request = get("/api/posts/" + post.getId() + "/comments").header(HttpHeaders.HOST, TestBlogs.host(blog));
        return mockMvc.perform(member == null ? request : request.cookie(testMembers.loginCookies(member)));
    }

}
