package com.nhnacademy.blog.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 재설정·잠금 안내 메일을 보낸다 (T088).
 * <ul>
 *   <li>{@code @TransactionalEventListener}: 토큰을 저장한 트랜잭션이 커밋된 뒤에만 보낸다. 저장이 롤백되면 쓸 수 없는 링크를 보내지 않는다.</li>
 *   <li>{@code @Async}: 다른 스레드에서 보낸다. 요청이 SMTP를 기다리지 않아, 가입한 이메일과 아닌 이메일의 응답 시간이 거의 같아진다
 *       (기다리면 Gmail처럼 먼 SMTP는 수백 ms가 걸려 응답 시간으로 가입 여부가 드러난다).</li>
 * </ul>
 * 실패해도 요청 쪽 응답은 이미 나갔으므로 로그로만 남긴다.
 */
@Component
public class ResetMailListener {

    private static final Logger log = LoggerFactory.getLogger(ResetMailListener.class);

    private final EmailSender emailSender;

    public ResetMailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener
    public void send(ResetMailRequested mail) {
        try {
            emailSender.send(mail.to(), mail.subject(), mail.text());
        } catch (RuntimeException e) {
            log.warn("비밀번호 재설정 메일을 보내지 못했습니다(회원 {}): {}", mail.memberId(), e.toString());
        }
    }

}
