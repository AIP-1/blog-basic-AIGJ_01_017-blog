package com.nhnacademy.blog.notification.domain;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {

    /** 읽지 않은 알림 수. 인덱스 idx_notification_receiver_id_read_at을 탄다. */
    long countByReceiverIdAndReadAtIsNull(Long receiverId);

    /** 모두 읽음. 읽은 시각만 바꾸는 UPDATE 한 번이다. */
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.receiverId = :receiverId and n.readAt is null")
    int markAllRead(@Param("receiverId") Long receiverId, @Param("now") LocalDateTime now);

}
