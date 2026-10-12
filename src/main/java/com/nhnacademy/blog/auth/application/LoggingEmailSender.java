package com.nhnacademy.blog.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 메일을 보내지 않고 로그로 남긴다. 서버 로그에서 인증 코드·재설정 링크를 확인한다.
 * app.mail.sender가 log이거나 없을 때 쓴다(실제 발송은 SmtpEmailSender).
 */
@Component
@ConditionalOnProperty(name = "app.mail.sender", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String text) {
        log.info("[개발용 메일] 받는 사람: {}, 제목: {}\n{}", to, subject, text);
    }

}
