package com.nhnacademy.blog.admin.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

    Optional<ModerationLog> findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
            ModerationTargetType targetType, Long targetId, ModerationAction action);

}
