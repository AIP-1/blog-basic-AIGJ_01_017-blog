package com.nhnacademy.blog.subscription.presentation.dto;

import com.nhnacademy.blog.subscription.application.SubscriptionResult;

/** `{ subscribed, subscriberCount }` (contracts/rest-api.md SUB). */
public record SubscriptionResponse(boolean subscribed, long subscriberCount) {

    public static SubscriptionResponse from(SubscriptionResult result) {
        return new SubscriptionResponse(result.subscribed(), result.subscriberCount());
    }

}
