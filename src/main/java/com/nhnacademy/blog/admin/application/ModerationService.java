package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLog;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.notification.application.NotificationService;
import com.nhnacademy.blog.notification.domain.NotificationTargetType;
import com.nhnacademy.blog.post.domain.Post;
import com.nhnacademy.blog.post.domain.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 조치 (ADMIN-02 정지, ADMIN-03 숨김, ADMIN-05 블로그 이용 제한). 신고 처리(ADMIN-04)도 이것을 부른다.
 * 조치마다 한 트랜잭션에서 대상 상태 바꾸기 → 관리 이력(moderation_log) INSERT → 받는 사람에게 SANCTION 알림.
 * 글·댓글 숨김은 작성자가 고친 것이 아니라서 수정 시각을 바꾸지 않는 UPDATE 한 문장으로 한다(엔티티를 고치면
 * 변경 감지와 @LastModifiedDate가 수정 시각을 지금으로 바꿔 화면에 "수정 …"이 붙는다).
 * 관리자는 남의 내용을 고치거나 지우지 않고 숨기거나 제한만 한다(헌법 원칙 V).
 * 이미 그 상태인 대상을 다시 해제하면 아무것도 하지 않는다(204, 이력 없음). 다시 제재하면 새 사유로 이력을 하나 더 남긴다.
 */
@Service
public class ModerationService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final int TITLE_IN_MESSAGE = 60;

    private final ModerationLogRepository moderationLogRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BlogRepository blogRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public ModerationService(ModerationLogRepository moderationLogRepository, MemberRepository memberRepository,
                             PostRepository postRepository, CommentRepository commentRepository,
                             BlogRepository blogRepository, NotificationService notificationService, Clock clock) {
        this.moderationLogRepository = moderationLogRepository;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.blogRepository = blogRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // ---------- 회원 정지 (ADMIN-02) ----------

    /** 정지. 없거나 탈퇴한 회원 404, 관리자 400. 정지 중에 다시 정지하면 기간과 사유를 새로 정한다. */
    @Transactional
    public void suspend(Long adminId, Long memberId, SuspensionPeriod period, Sanction sanction) {
        Member member = member(memberId);
        if (member.isAdmin()) {
            throw BusinessException.invalidField("member", "관리자는 정지할 수 없습니다.");
        }
        LocalDateTime until = period.until(now());
        member.suspend(until);
        record(adminId, ModerationAction.SUSPEND, ModerationTargetType.MEMBER, memberId, sanction);
        notificationService.sanctioned(memberId, NotificationTargetType.MEMBER, memberId, until == null
                ? "운영 정책 위반(" + sanction.label() + ")으로 이용이 영구 정지되었습니다."
                : "운영 정책 위반(" + sanction.label() + ")으로 " + DATE.format(until) + "까지 이용이 정지되었습니다.");
    }

    /** 해제. 정지 중이 아니면(기간이 지난 것 포함) 상태만 정리하고 이력·알림은 없다. */
    @Transactional
    public void unsuspend(Long adminId, Long memberId) {
        Member member = member(memberId);
        if (!member.isSuspendedAt(now())) {
            member.unsuspend();
            return;
        }
        member.unsuspend();
        record(adminId, ModerationAction.UNSUSPEND, ModerationTargetType.MEMBER, memberId, null);
        notificationService.sanctioned(memberId, NotificationTargetType.MEMBER, memberId, "이용 정지가 해제되었습니다.");
    }

    // ---------- 글·댓글 숨김 (ADMIN-03) ----------

    @Transactional
    public void blindPost(Long adminId, Long postId, Sanction sanction) {
        Post post = post(postId);
        Long ownerId = post.getBlog().getMember().getId();
        String title = post.getTitle();
        postRepository.changeBlinded(postId, true);
        record(adminId, ModerationAction.BLIND, ModerationTargetType.POST, postId, sanction);
        notificationService.sanctioned(ownerId, NotificationTargetType.POST, postId,
                "\"" + shorten(title) + "\" 글이 운영 정책 위반(" + sanction.label() + ")으로 숨김 처리되었습니다.");
    }

    @Transactional
    public void unblindPost(Long adminId, Long postId) {
        Post post = post(postId);
        if (!post.isBlinded()) {
            return;
        }
        Long ownerId = post.getBlog().getMember().getId();
        String title = post.getTitle();
        postRepository.changeBlinded(postId, false);
        record(adminId, ModerationAction.UNBLIND, ModerationTargetType.POST, postId, null);
        notificationService.sanctioned(ownerId, NotificationTargetType.POST, postId,
                "\"" + shorten(title) + "\" 글의 숨김이 해제되었습니다.");
    }

    @Transactional
    public void blindComment(Long adminId, Long commentId, Sanction sanction) {
        Comment comment = comment(commentId);
        Long authorId = comment.getMember().getId();
        String title = comment.getPost().getTitle();
        commentRepository.changeBlinded(commentId, true);
        record(adminId, ModerationAction.BLIND, ModerationTargetType.COMMENT, commentId, sanction);
        notificationService.sanctioned(authorId, NotificationTargetType.COMMENT, commentId,
                "\"" + shorten(title) + "\"에 쓴 댓글이 운영 정책 위반(" + sanction.label() + ")으로 숨김 처리되었습니다.");
    }

    @Transactional
    public void unblindComment(Long adminId, Long commentId) {
        Comment comment = comment(commentId);
        if (!comment.isBlinded()) {
            return;
        }
        Long authorId = comment.getMember().getId();
        String title = comment.getPost().getTitle();
        commentRepository.changeBlinded(commentId, false);
        record(adminId, ModerationAction.UNBLIND, ModerationTargetType.COMMENT, commentId, null);
        notificationService.sanctioned(authorId, NotificationTargetType.COMMENT, commentId,
                "\"" + shorten(title) + "\"에 쓴 댓글의 숨김이 해제되었습니다.");
    }

    // ---------- 블로그 이용 제한 (ADMIN-05) ----------

    @Transactional
    public void restrictBlog(Long adminId, Long blogId, Sanction sanction) {
        Blog blog = blog(blogId);
        blog.changeRestricted(true);
        record(adminId, ModerationAction.RESTRICT_BLOG, ModerationTargetType.BLOG, blogId, sanction);
        notificationService.sanctioned(blog.getMember().getId(), NotificationTargetType.BLOG, blogId,
                shorten(blog.getName()) + " 블로그가 운영 정책 위반(" + sanction.label() + ")으로 이용 제한되었습니다.");
    }

    @Transactional
    public void unrestrictBlog(Long adminId, Long blogId) {
        Blog blog = blog(blogId);
        if (!blog.isRestricted()) {
            return;
        }
        blog.changeRestricted(false);
        record(adminId, ModerationAction.UNRESTRICT_BLOG, ModerationTargetType.BLOG, blogId, null);
        notificationService.sanctioned(blog.getMember().getId(), NotificationTargetType.BLOG, blogId,
                shorten(blog.getName()) + " 블로그의 이용 제한이 해제되었습니다.");
    }

    /** 신고 기각 (ADMIN-04). 대상에는 아무것도 하지 않고 이력만 남긴다. 대상은 신고된 것 그대로 적는다. */
    @Transactional
    public void rejectReport(Long adminId, ModerationTargetType targetType, Long targetId) {
        record(adminId, ModerationAction.REJECT_REPORT, targetType, targetId, null);
    }

    // ---------- 대상 찾기 ----------

    /** 없거나 탈퇴한 회원 404. */
    public Member member(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> !member.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 없거나 지운 글 404. 임시저장 글도 숨길 수는 있다(발행하면 숨긴 채로 나간다). */
    @Transactional(readOnly = true)
    public Post post(Long postId) {
        return postRepository.findWithBlogById(postId)
                .filter(post -> post.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 없거나 지운 댓글 404. */
    @Transactional(readOnly = true)
    public Comment comment(Long commentId) {
        return commentRepository.findWithPostById(commentId)
                .filter(comment -> !comment.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 없거나 지운 블로그 404. */
    @Transactional(readOnly = true)
    public Blog blog(Long blogId) {
        return blogRepository.findWithMemberById(blogId)
                .filter(blog -> !blog.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void record(Long adminId, ModerationAction action, ModerationTargetType targetType, Long targetId,
                        Sanction sanction) {
        moderationLogRepository.save(ModerationLog.record(memberRepository.getReferenceById(adminId), action,
                targetType, targetId, sanction == null ? null : sanction.reason(),
                sanction == null ? null : sanction.reasonDetail()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String shorten(String text) {
        return text.length() <= TITLE_IN_MESSAGE ? text : text.substring(0, TITLE_IN_MESSAGE) + "…";
    }

}
