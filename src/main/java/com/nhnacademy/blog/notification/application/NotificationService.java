package com.nhnacademy.blog.notification.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import com.nhnacademy.blog.global.visibility.PostAccess;
import com.nhnacademy.blog.global.visibility.PostVisibilityPolicy;
import com.nhnacademy.blog.global.web.TimeIdCursor;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.notification.domain.Notification;
import com.nhnacademy.blog.notification.domain.NotificationRepository;
import com.nhnacademy.blog.notification.domain.NotificationTargetType;
import com.nhnacademy.blog.notification.domain.NotificationType;
import com.nhnacademy.blog.post.domain.Post;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 (T113, SUB-04). 내 글의 댓글, 내 댓글의 답글, 내 글의 공감, 내 블로그의 새 구독자가 생기면 만든다.
 * 내가 한 일은 나에게 알리지 않는다. 만드는 쪽(댓글·공감·구독 서비스)의 트랜잭션 안에서 함께 저장된다.
 * 읽을 때는 대상이 지워졌거나 받는 사람이 볼 수 없게 된 알림을 뺀다(contracts SUB-04). 제재·해제(SANCTION)는 스텝 18.
 */
@Service
public class NotificationService {

    public static final int PAGE_SIZE = 20;

    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notificationRepository;
    private final CommentRepository commentRepository;
    private final BlogRepository blogRepository;
    private final PostVisibilityPolicy postVisibilityPolicy;
    private final BlogVisibilityPolicy blogVisibilityPolicy;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, CommentRepository commentRepository,
                               BlogRepository blogRepository, PostVisibilityPolicy postVisibilityPolicy,
                               BlogVisibilityPolicy blogVisibilityPolicy, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.commentRepository = commentRepository;
        this.blogRepository = blogRepository;
        this.postVisibilityPolicy = postVisibilityPolicy;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
        this.clock = clock;
    }

    /**
     * 댓글·답글이 달렸다. 답글이면 부모 댓글 작성자에게 REPLY, 글 주인에게 COMMENT.
     * 글 주인이 부모 댓글 작성자이기도 하면 REPLY 하나만 보낸다(같은 일로 두 번 알리지 않음).
     */
    public void commented(Comment comment) {
        Member author = comment.getMember();
        Post post = comment.getPost();
        Long ownerId = post.getBlog().getMember().getId();
        Long parentAuthorId = comment.getParent() == null ? null : comment.getParent().getMember().getId();
        if (parentAuthorId != null) {
            send(author, parentAuthorId, NotificationType.REPLY, NotificationTargetType.COMMENT, comment.getId(),
                    author.getNickname() + "님이 내 댓글에 답글을 남겼습니다.");
        }
        if (!ownerId.equals(parentAuthorId)) {
            send(author, ownerId, NotificationType.COMMENT, NotificationTargetType.COMMENT, comment.getId(),
                    author.getNickname() + "님이 \"" + post.getTitle() + "\"에 댓글을 남겼습니다.");
        }
    }

    /** 공감이 새로 눌렸다(이미 누른 것을 다시 누른 것은 아님). */
    public void liked(Post post, Member liker) {
        send(liker, post.getBlog().getMember().getId(), NotificationType.LIKE, NotificationTargetType.POST,
                post.getId(), liker.getNickname() + "님이 \"" + post.getTitle() + "\"에 공감했습니다.");
    }

    /** 새 구독자가 생겼다. */
    public void subscribed(Blog blog, Member subscriber) {
        send(subscriber, blog.getMember().getId(), NotificationType.SUBSCRIBE, NotificationTargetType.BLOG,
                blog.getId(), subscriber.getNickname() + "님이 " + blog.getName() + "을(를) 구독했습니다.");
    }

    private void send(Member actor, Long receiverId, NotificationType type, NotificationTargetType targetType,
                      Long targetId, String message) {
        if (receiverId.equals(actor.getId())) {
            return;
        }
        notificationRepository.save(Notification.of(receiverId, type, targetType, targetId, message));
    }

    /** 최신순 20개씩. 볼 수 없게 된 대상의 알림은 빼고, 다음 묶음은 읽은 행 기준으로 이어 간다. */
    @Transactional(readOnly = true)
    public NotificationPage list(Long receiverId, TimeIdCursor cursor) {
        Specification<Notification> condition = (root, query, cb) -> cb.equal(root.get("receiverId"), receiverId);
        if (cursor != null) {
            condition = condition.and((root, query, cb) -> cb.or(
                    cb.lessThan(root.get("createdAt"), cursor.time()),
                    cb.and(cb.equal(root.get("createdAt"), cursor.time()), cb.lessThan(root.get("id"), cursor.id()))));
        }
        List<Notification> fetched = notificationRepository.findBy(condition,
                query -> query.sortBy(NEWEST).limit(PAGE_SIZE + 1).all());
        boolean more = fetched.size() > PAGE_SIZE;
        List<Notification> page = more ? fetched.subList(0, PAGE_SIZE) : fetched;
        List<NotificationView> items = new ArrayList<>();
        for (Notification notification : page) {
            view(notification).ifPresent(items::add);
        }
        Notification last = page.isEmpty() ? null : page.getLast();
        return new NotificationPage(items, more ? new TimeIdCursor(last.getCreatedAt(), last.getId()) : null);
    }

    /** 머리글의 알림 수. 볼 수 없게 된 대상의 알림도 읽기 전까지는 센다(목록과 다를 수 있다, 스텝 16 노트). */
    @Transactional(readOnly = true)
    public long unreadCount(Long receiverId) {
        return notificationRepository.countByReceiverIdAndReadAtIsNull(receiverId);
    }

    /** 읽음. 내 알림이 아니거나 없으면 404(남의 알림이 있는지 알리지 않음). */
    @Transactional
    public void read(Long receiverId, Long notificationId) {
        notificationRepository.findById(notificationId)
                .filter(notification -> notification.getReceiverId().equals(receiverId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND))
                .markRead(LocalDateTime.now(clock));
    }

    @Transactional
    public void readAll(Long receiverId) {
        notificationRepository.markAllRead(receiverId, LocalDateTime.now(clock));
    }

    /** 받는 사람이 아직 볼 수 있으면 갈 곳과 함께. 글·댓글은 글 상세와 같은 가시성 판단을 쓴다. */
    private Optional<NotificationView> view(Notification notification) {
        Long receiverId = notification.getReceiverId();
        return switch (notification.getTargetType()) {
            case COMMENT -> commentRepository.findWithPostById(notification.getTargetId())
                    .filter(comment -> !comment.isDeleted())
                    .flatMap(comment -> readable(comment.getPost(), receiverId)
                            .map(blog -> new NotificationView(notification, blog,
                                    "/" + comment.getPost().getId() + "#comment-" + comment.getId())));
            case POST -> {
                PostAccess access = postVisibilityPolicy.decide(notification.getTargetId(), null, receiverId);
                yield access.canRead()
                        ? Optional.of(new NotificationView(notification, postOf(access).getBlog(),
                        "/" + notification.getTargetId()))
                        : Optional.empty();
            }
            case BLOG -> blogRepository.findWithMemberById(notification.getTargetId())
                    .filter(blog -> blogVisibilityPolicy.canView(blog, receiverId) && !blog.isMoved())
                    .map(blog -> new NotificationView(notification, blog, "/"));
            case MEMBER -> Optional.of(new NotificationView(notification, null, "/me"));
        };
    }

    private Optional<Blog> readable(Post post, Long receiverId) {
        PostAccess access = postVisibilityPolicy.decide(post, null, receiverId);
        return access.canRead() ? Optional.of(post.getBlog()) : Optional.empty();
    }

    private static Post postOf(PostAccess access) {
        return switch (access) {
            case PostAccess.Owner owner -> owner.post();
            case PostAccess.Visible visible -> visible.post();
            default -> throw new IllegalStateException("읽을 수 없는 글");
        };
    }

}
