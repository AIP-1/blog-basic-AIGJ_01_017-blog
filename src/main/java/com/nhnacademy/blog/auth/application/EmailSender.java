package com.nhnacademy.blog.auth.application;

/**
 * 메일 보내기. app.mail.sender로 고른다: log면 LoggingEmailSender가 로그로만 남기고(T017),
 * smtp면 SmtpEmailSender가 실제로 보낸다(T088).
 */
public interface EmailSender {

    void send(String to, String subject, String text);

}
