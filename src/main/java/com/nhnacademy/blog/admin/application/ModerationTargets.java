package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.post.domain.PostRepository;
import org.springframework.stereotype.Component;

/**
 * 관리 화면에 보일 대상 한 줄 (신고 묶음, 관리 이력, 대시보드). 대상의 지금 상태를 읽어 이름과 갈 곳을 정한다.
 * 관리자 화면이라 숨김·비공개와 상관없이 그대로 보여 준다. 지워졌으면 exists가 false다.
 */
@Component
public class ModerationTargets {

    private static final int PREVIEW = 60;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BlogRepository blogRepository;
    private final MemberRepository memberRepository;

    public ModerationTargets(PostRepository postRepository, CommentRepository commentRepository,
                             BlogRepository blogRepository, MemberRepository memberRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
    }

    /** 트랜잭션 안에서 부른다(글의 블로그, 댓글의 글을 지연 로딩). */
    public TargetView of(ModerationTargetType type, Long id) {
        return switch (type) {
            case POST -> postRepository.findWithBlogById(id)
                    .map(post -> new TargetView(type, id, post.getTitle(), post.getBlog().getAddress(), post.getId(),
                            null, post.getDeletedAt() == null, post.isBlinded()))
                    .orElse(TargetView.missing(type, id));
            case COMMENT -> commentRepository.findWithPostById(id)
                    .map(comment -> new TargetView(type, id, preview(comment.getContent()),
                            comment.getPost().getBlog().getAddress(), comment.getPost().getId(),
                            comment.getMember().getId(), !comment.isDeleted(), comment.isBlinded()))
                    .orElse(TargetView.missing(type, id));
            case BLOG -> blogRepository.findWithMemberById(id)
                    .map(blog -> new TargetView(type, id, blog.getName() + " (" + blog.getAddress() + ")",
                            blog.getAddress(), null, blog.getMember().getId(), !blog.isDeleted(), blog.isRestricted()))
                    .orElse(TargetView.missing(type, id));
            case MEMBER -> memberRepository.findById(id)
                    .map(member -> new TargetView(type, id, member.getNickname(), null, null, member.getId(),
                            !member.isWithdrawn(), member.getStatus().name().equals("SUSPENDED")))
                    .orElse(TargetView.missing(type, id));
            case REPORT -> new TargetView(type, id, "신고 " + id, null, null, null, true, false);
        };
    }

    private static String preview(String text) {
        return text.length() <= PREVIEW ? text : text.substring(0, PREVIEW) + "…";
    }

    /**
     * 대상 한 줄. blogAddress·postId는 화면이 링크를 만들 때 쓴다(글이면 그 블로그의 /{postId}, 블로그면 홈).
     * memberId는 댓글·블로그의 작성자·주인(회원 상세로 갈 때). sanctioned는 지금 숨김·제한·정지 중인가.
     */
    public record TargetView(ModerationTargetType type, Long id, String label, String blogAddress, Long postId,
                             Long memberId, boolean exists, boolean sanctioned) {

        static TargetView missing(ModerationTargetType type, Long id) {
            return new TargetView(type, id, null, null, null, null, false, false);
        }

    }

}
