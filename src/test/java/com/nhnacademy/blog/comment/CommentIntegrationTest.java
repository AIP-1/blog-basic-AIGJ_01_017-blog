package com.nhnacademy.blog.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.image.domain.Image;
import com.nhnacademy.blog.image.domain.ImageRepository;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.Visibility;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import com.nhnacademy.blog.support.TestPosts;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 댓글 쓰기·보기·지우기 (T044, CMT-01, CMT-02, spec US3 시나리오 6·7·9).
 */
class CommentIntegrationTest extends IntegrationTestSupport {

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

    @Autowired
    ImageRepository imageRepository;

    @Autowired
    MemberRepository memberRepository;

    Member owner;
    Member reader;
    Blog blog;
    Post post;

    @BeforeEach
    void setUp() {
        owner = testMembers.create();
        reader = testMembers.create();
        blog = testBlogs.create(owner);
        post = testPosts.published(blog, Visibility.PUBLIC);
    }

    @Test
    void memberWritesCommentAndCountGoesUp() throws Exception {
        jdbcTemplate.update("UPDATE post SET updated_at = created_at WHERE id = ?", post.getId());

        write(reader, post, "{\"content\":\"  잘 읽었습니다 \"}", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("잘 읽었습니다"))
                .andExpect(jsonPath("$.author.nickname").value(reader.getNickname()))
                .andExpect(jsonPath("$.state").value("NORMAL"))
                .andExpect(jsonPath("$.viewer.canDelete").value(true))
                .andExpect(jsonPath("$.replies").isEmpty());

        assertThat(commentCount(post)).isEqualTo(1);
        // 댓글이 달린 것은 글을 고친 것이 아니다
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at = created_at FROM post WHERE id = ?",
                Boolean.class, post.getId())).isTrue();
        mockMvc.perform(get("/api/posts/" + post.getId()).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void commentAuthorLinksToTheirPrimaryBlogOnlyWhenTheyHaveOne() throws Exception {
        Blog readerBlog = testBlogs.createPrimary(reader);
        Member noBlog = testMembers.create();
        write(reader, post, "{\"content\":\"블로그 있는 사람\"}", UUID.randomUUID().toString());
        write(noBlog, post, "{\"content\":\"블로그 없는 사람\"}", UUID.randomUUID().toString());

        // 화면은 이 주소로 닉네임 링크를 만든다(BLOG-08). 블로그가 없으면 링크 없이 닉네임만
        list(post, null, null)
                .andExpect(jsonPath("$.content[0].author.primaryBlogAddress").value(readerBlog.getAddress()))
                .andExpect(jsonPath("$.content[1].author.nickname").value(noBlog.getNickname()))
                .andExpect(jsonPath("$.content[1].author.primaryBlogAddress").doesNotExist());
        // 대표 블로그가 이용 제한되면 볼 수 없는 블로그라 링크도 없다
        testBlogs.restrict(readerBlog);
        list(post, null, null).andExpect(jsonPath("$.content[0].author.primaryBlogAddress").doesNotExist());
    }

    @Test
    void authorsCarryTheirProfilePhotoInCommentsAndPostDetail() throws Exception {
        String readerPhoto = setProfilePhoto(reader);
        String ownerPhoto = setProfilePhoto(owner);
        Member noPhoto = testMembers.create();
        write(reader, post, "{\"content\":\"사진 있는 사람\"}", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.author.profileImageUrl").value(readerPhoto));
        write(noPhoto, post, "{\"content\":\"사진 없는 사람\"}", UUID.randomUUID().toString());

        // 댓글 옆 동그라미와 글쓴이 줄에 회원 프로필 사진(AUTH-05)이 보인다. 사진이 없으면 null
        list(post, null, null)
                .andExpect(jsonPath("$.content[0].author.profileImageUrl").value(readerPhoto))
                .andExpect(jsonPath("$.content[1].author.profileImageUrl").doesNotExist());
        mockMvc.perform(get("/api/posts/" + post.getId()).header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.author.profileImageUrl").value(ownerPhoto));
        mockMvc.perform(get("/api/blog").header(HttpHeaders.HOST, TestBlogs.host(blog)))
                .andExpect(jsonPath("$.owner.profileImageUrl").value(ownerPhoto));
    }

    @Test
    void doubleClickMakesOneComment() throws Exception {
        String key = UUID.randomUUID().toString();

        write(reader, post, "{\"content\":\"한 번만\"}", key).andExpect(status().isCreated());
        write(reader, post, "{\"content\":\"한 번만\"}", key).andExpect(status().isCreated());

        assertThat(commentCount(post)).isEqualTo(1);
        list(post, null, null).andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void anonymousIsAskedToLoginButHiddenPostIs404() throws Exception {
        Post privatePost = testPosts.published(blog, Visibility.PRIVATE);

        write(null, post, "{\"content\":\"비회원\"}", UUID.randomUUID().toString())
                .andExpect(status().isUnauthorized());
        write(null, privatePost, "{\"content\":\"비회원\"}", UUID.randomUUID().toString())
                .andExpect(status().isNotFound());
        write(reader, privatePost, "{\"content\":\"남의 비공개\"}", UUID.randomUUID().toString())
                .andExpect(status().isNotFound());
        list(privatePost, null, null).andExpect(status().isNotFound());
    }

    @Test
    void invalidOrUnsupportedInputIs400() throws Exception {
        write(reader, post, "{\"content\":\"   \"}", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
        write(reader, post, "{\"content\":\"" + "가".repeat(1001) + "\"}", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest());
        write(reader, post, "{\"content\":\"답글\",\"parentId\":1}", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentId"));
        write(reader, post, "{\"content\":\"비밀\",\"secret\":true}", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("secret"));
        write(reader, post, "{\"content\":\"" + "가".repeat(1000) + "\"}", UUID.randomUUID().toString())
                .andExpect(status().isCreated());
    }

    @Test
    void commentsDisabledPostRejects() throws Exception {
        jdbcTemplate.update("UPDATE post SET is_comment_allowed = 0 WHERE id = ?", post.getId());

        write(reader, post, "{\"content\":\"\"}", UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENTS_DISABLED"));
    }

    @Test
    void listIsInWrittenOrderTwentyAtATime() throws Exception {
        for (int i = 0; i < 22; i++) {
            commentRepository.save(Comment.write(post, reader, "댓글 " + i, false));
        }

        String first = list(post, null, null)
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.content[0].content").value("댓글 0"))
                .andExpect(jsonPath("$.content[19].content").value("댓글 19"))
                .andExpect(jsonPath("$.totalCount").value(22))
                .andReturn().getResponse().getContentAsString();

        list(post, null, JsonPath.read(first, "$.nextCursor"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].content").value("댓글 20"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void secretAndBlindedCommentsAreMasked() throws Exception {
        commentRepository.save(Comment.write(post, reader, "비밀 이야기", true));
        Comment blinded = commentRepository.save(Comment.write(post, reader, "광고", false));
        Comment deleted = commentRepository.save(Comment.write(post, reader, "지운 댓글", false));
        jdbcTemplate.update("UPDATE comment SET is_blinded = 1 WHERE id = ?", blinded.getId());
        jdbcTemplate.update("UPDATE comment SET deleted_at = NOW() WHERE id = ?", deleted.getId());
        Member stranger = testMembers.create();

        list(post, stranger, null)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].state").value("SECRET"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.content[0].author").doesNotExist())
                .andExpect(jsonPath("$.content[1].state").value("BLINDED"))
                .andExpect(jsonPath("$.content[1].content").doesNotExist());
        // 비밀댓글은 글 주인과 작성자가 본다. 숨긴 댓글은 작성자에게만 내용이 보인다
        list(post, owner, null)
                .andExpect(jsonPath("$.content[0].content").value("비밀 이야기"))
                .andExpect(jsonPath("$.content[1].state").value("BLINDED"));
        list(post, reader, null)
                .andExpect(jsonPath("$.content[0].state").value("NORMAL"))
                .andExpect(jsonPath("$.content[1].content").value("광고"));
    }

    @Test
    void authorAndBlogOwnerCanDeleteOthersCannot() throws Exception {
        Comment byReader = commentRepository.save(Comment.write(post, reader, "지울 댓글", false));
        Comment second = commentRepository.save(Comment.write(post, reader, "주인이 지울 댓글", false));
        jdbcTemplate.update("UPDATE post SET comment_count = 2 WHERE id = ?", post.getId());
        Member stranger = testMembers.create();

        remove(byReader, null).andExpect(status().isUnauthorized());
        remove(byReader, stranger).andExpect(status().isForbidden());
        list(post, stranger, null).andExpect(jsonPath("$.content[0].viewer.canDelete").value(false));
        list(post, owner, null).andExpect(jsonPath("$.content[0].viewer.canDelete").value(true));

        remove(byReader, reader).andExpect(status().isNoContent());
        remove(second, owner).andExpect(status().isNoContent());
        remove(second, owner).andExpect(status().isNotFound());

        assertThat(commentCount(post)).isZero();
        list(post, null, null).andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void commentOfAnotherBlogIs404OnThisAddress() throws Exception {
        Blog otherBlog = testBlogs.create(testMembers.create());
        Post otherPost = testPosts.published(otherBlog, Visibility.PUBLIC);
        Comment elsewhere = commentRepository.save(Comment.write(otherPost, reader, "다른 블로그", false));

        remove(elsewhere, reader).andExpect(status().isNotFound());
        assertThat(commentRepository.findById(elsewhere.getId()).orElseThrow().isDeleted()).isFalse();
    }

    /** 파일 없이 이미지 행만 만들어 회원 프로필 사진으로 건다. 썸네일 주소를 돌려준다. */
    private String setProfilePhoto(Member member) {
        String thumbnail = "/uploads/t_member" + member.getId() + ".png";
        Image image = imageRepository.save(Image.uploaded(member.getId(), "/uploads/member" + member.getId() + ".png",
                thumbnail, "a.png", "image/png", 10));
        Member found = memberRepository.findById(member.getId()).orElseThrow();
        found.changeProfileImage(image.getId());
        memberRepository.save(found);
        return thumbnail;
    }

    private int commentCount(Post target) {
        return jdbcTemplate.queryForObject("SELECT comment_count FROM post WHERE id = ?", Integer.class,
                target.getId());
    }

    private ResultActions write(Member member, Post target, String body, String key) throws Exception {
        var request = post("/api/posts/" + target.getId() + "/comments")
                .header(HttpHeaders.HOST, TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return mockMvc.perform(member == null ? request : request.cookie(cookies(member)));
    }

    private ResultActions list(Post target, Member member, String cursor) throws Exception {
        var request = get("/api/posts/" + target.getId() + "/comments").header(HttpHeaders.HOST, TestBlogs.host(blog));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        return mockMvc.perform(member == null ? request : request.cookie(cookies(member)));
    }

    private ResultActions remove(Comment comment, Member member) throws Exception {
        var request = delete("/api/comments/" + comment.getId())
                .header(HttpHeaders.HOST, TestBlogs.host(blog))
                .header("X-Requested-With", "XMLHttpRequest");
        return mockMvc.perform(member == null ? request : request.cookie(cookies(member)));
    }

    private Cookie[] cookies(Member member) {
        return testMembers.loginCookies(member);
    }

}
