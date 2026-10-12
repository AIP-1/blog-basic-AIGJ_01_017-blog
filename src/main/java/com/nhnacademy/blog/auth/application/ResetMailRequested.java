package com.nhnacademy.blog.auth.application;

/** 재설정·잠금 안내 메일을 보내 달라는 이벤트 (T088). 트랜잭션이 끝난 뒤 다른 스레드에서 보낸다(ResetMailListener). */
public record ResetMailRequested(Long memberId, String to, String subject, String text) {
}
