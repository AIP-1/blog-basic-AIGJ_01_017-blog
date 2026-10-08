package com.nhnacademy.blog.auth.application;

/**
 * 메일 보내기. 개발 중에는 LoggingEmailSender가 로그로만 남긴다(T017).
 * 실제 메일 발송은 이 인터페이스의 구현을 하나 더 만들어 바꾼다.
 */
public interface EmailSender {

    void send(String to, String subject, String text);

}
