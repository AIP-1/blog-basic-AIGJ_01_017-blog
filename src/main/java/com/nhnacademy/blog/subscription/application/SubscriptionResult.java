package com.nhnacademy.blog.subscription.application;

/** 구독·해제 결과 (SUB-01, SUB-03). 화면이 버튼과 구독자 수를 바로 바꾼다. */
public record SubscriptionResult(boolean subscribed, long subscriberCount) {
}
