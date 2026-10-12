package com.nhnacademy.blog.support;

import com.nhnacademy.blog.auth.application.EmailSender;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트용 메일함. 보낸 메일을 보내지 않고 모아 둔다(T088). 재설정 링크의 토큰은 DB에 해시만 있어서 메일에서 꺼내야 한다.
 * 테스트끼리 함께 쓰므로 받는 사람(매번 다른 이메일)으로 골라 본다.
 */
@Component
@Primary
public class RecordingEmailSender implements EmailSender {

    private static final Pattern RESET_TOKEN = Pattern.compile("/password-reset\\?token=([A-Za-z0-9_-]+)");

    private final Queue<Mail> mails = new ConcurrentLinkedQueue<>();

    @Override
    public void send(String to, String subject, String text) {
        mails.add(new Mail(to, subject, text));
    }

    public List<Mail> to(String email) {
        return mails.stream().filter(mail -> mail.to().equals(email)).toList();
    }

    /** 재설정 메일은 다른 스레드에서 가므로(ResetMailListener) 그 이메일로 count통이 올 때까지 5초 기다린다. */
    public List<Mail> awaitTo(String email, int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (to(email).size() < count) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(email + "로 메일 " + count + "통이 5초 안에 오지 않았습니다");
            }
            Thread.sleep(20);
        }
        return to(email);
    }

    /** 그 이메일로 마지막에 보낸 메일의 재설정 토큰. */
    public Optional<String> lastResetToken(String email) {
        List<Mail> received = to(email);
        for (int i = received.size() - 1; i >= 0; i--) {
            Matcher matcher = RESET_TOKEN.matcher(received.get(i).text());
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    public record Mail(String to, String subject, String text) {
    }

}
