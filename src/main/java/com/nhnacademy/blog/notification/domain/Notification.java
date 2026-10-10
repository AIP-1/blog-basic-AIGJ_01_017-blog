package com.nhnacademy.blog.notification.domain;

import com.nhnacademy.blog.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 알림 (SUB-04). 받는 회원 한 명당 한 행이다. 표시 문구(message)는 만들 때 정해 둔다(나중에 닉네임·제목이 바뀌어도
 * 그때 일어난 일을 그대로 보여 준다). 누르면 갈 주소는 대상(target)으로 읽을 때 만든다.
 * 받는 회원은 번호만 둔다(알림을 읽을 때 회원 엔티티가 필요 없다).
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseCreatedEntity {

    public static final int MESSAGE_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "message", nullable = false, length = MESSAGE_LENGTH)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {
    }

    private Notification(Long receiverId, NotificationType type, NotificationTargetType targetType, Long targetId,
                         String message) {
        this.receiverId = receiverId;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.message = message.length() > MESSAGE_LENGTH ? message.substring(0, MESSAGE_LENGTH - 1) + "…" : message;
    }

    public static Notification of(Long receiverId, NotificationType type, NotificationTargetType targetType,
                                  Long targetId, String message) {
        return new Notification(receiverId, type, targetType, targetId, message);
    }

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

}
