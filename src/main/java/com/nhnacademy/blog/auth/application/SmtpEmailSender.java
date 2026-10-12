package com.nhnacademy.blog.auth.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP로 실제 메일을 보낸다 (T088). app.mail.sender=smtp일 때만 만들어지고, 접속 정보는 spring.mail.*이다
 * (Spring Boot가 그 값으로 JavaMailSender를 만든다). 개발에서는 Mailpit(localhost:1025)이 받아 http://localhost:8025 에 보여 준다.
 */
@Component
@ConditionalOnProperty(name = "app.mail.sender", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

}
