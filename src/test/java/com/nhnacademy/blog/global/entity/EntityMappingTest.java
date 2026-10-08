package com.nhnacademy.blog.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nhnacademy.blog.TestcontainersConfiguration;
import com.nhnacademy.blog.blog.AccentColor;
import com.nhnacademy.blog.blog.Blog;
import com.nhnacademy.blog.blog.BlogRepository;
import com.nhnacademy.blog.blog.ListLayout;
import com.nhnacademy.blog.category.Category;
import com.nhnacademy.blog.category.CategoryRepository;
import com.nhnacademy.blog.comment.Comment;
import com.nhnacademy.blog.comment.CommentRepository;
import com.nhnacademy.blog.member.Member;
import com.nhnacademy.blog.member.MemberRepository;
import com.nhnacademy.blog.member.MemberStatus;
import com.nhnacademy.blog.member.Role;
import com.nhnacademy.blog.post.Post;
import com.nhnacademy.blog.post.PostRepository;
import com.nhnacademy.blog.post.PostStatus;
import com.nhnacademy.blog.post.Topic;
import com.nhnacademy.blog.post.Visibility;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 엔티티를 실제 MySQL(schema.sql)에 저장하고 다시 읽어 컬럼 매핑과 기본값을 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EntityMappingTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void saveAndLoadCoreEntities() {
        Member member = memberRepository.save(Member.ofEmail("a@blog.test", "hash", "에이"));
        Blog blog = blogRepository.save(Blog.open(member, "alpha", "알파 블로그", true));
        Category category = categoryRepository.save(Category.create(blog, null, "Java", 0));
        Post post = postRepository.save(Post.published(blog, category, "제목", "<p>본문</p>", "본문",
                Visibility.PUBLIC, Topic.IT_DEV, LocalDateTime.now()));
        Comment comment = commentRepository.save(Comment.write(post, member, "댓글", false));
        Comment reply = commentRepository.save(Comment.reply(comment, member, "답글", true));

        entityManager.flush();
        entityManager.clear();

        Member savedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(savedMember.getRole()).isEqualTo(Role.USER);
        assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(savedMember.getCreatedAt()).isNotNull();
        assertThat(savedMember.getUpdatedAt()).isNotNull();

        Blog savedBlog = blogRepository.findById(blog.getId()).orElseThrow();
        assertThat(savedBlog.isPrimary()).isTrue();
        assertThat(savedBlog.getListLayout()).isEqualTo(ListLayout.LIST);
        assertThat(savedBlog.getAccentColor()).isEqualTo(AccentColor.BLUE);
        assertThat(savedBlog.isOwnedBy(member.getId())).isTrue();

        Category savedCategory = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getUpdatedAt()).isNotNull();

        Post savedPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(savedPost.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(savedPost.getTopic()).isEqualTo(Topic.IT_DEV);
        assertThat(savedPost.getCategory().getId()).isEqualTo(category.getId());
        assertThat(savedPost.isCommentAllowed()).isTrue();

        Comment savedReply = commentRepository.findById(reply.getId()).orElseThrow();
        assertThat(savedReply.getParent().getId()).isEqualTo(comment.getId());
        assertThat(savedReply.getPost().getId()).isEqualTo(post.getId());
        assertThat(savedReply.isSecret()).isTrue();
    }

}
