package com.nhnacademy.blog.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켠다(예약 발행 ScheduledPublisher, T103).
 * 테스트는 app.scheduling.enabled=false로 꺼 둔다. 백그라운드에서 1분마다 돌면 "예약 시각 전에는 안 보인다"를
 * 확인하는 테스트가 그 사이에 발행되어 흔들릴 수 있어서, 테스트는 publishDue를 직접 부른다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
