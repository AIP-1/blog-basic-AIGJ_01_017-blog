package com.nhnacademy.blog.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 메일을 보내지 않고 로그로 남긴다. 개발 중에는 서버 로그에서 인증 코드를 확인한다.
 */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String text) {
        log.info("[개발용 메일] 받는 사람: {}, 제목: {}\n{}", to, subject, text);
    }

}
