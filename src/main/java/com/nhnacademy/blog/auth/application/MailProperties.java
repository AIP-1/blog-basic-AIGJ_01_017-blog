package com.nhnacademy.blog.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 보내는 방법 (T088).
 *
 * @param sender log(보내지 않고 서버 로그에만, 기본) 또는 smtp(spring.mail로 실제 발송: 개발은 Mailpit, 운영은 Gmail 등)
 * @param from   받는 사람에게 보이는 보낸 사람. Gmail이면 그 Gmail 주소와 같아야 한다
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String sender, String from) {
}
