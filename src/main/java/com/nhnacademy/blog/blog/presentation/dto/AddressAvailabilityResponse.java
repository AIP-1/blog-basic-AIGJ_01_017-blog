package com.nhnacademy.blog.blog.presentation.dto;

import com.nhnacademy.blog.blog.application.AddressCheck;

/** reason: INVALID, RESERVED, TAKEN. 쓸 수 있으면 null. */
public record AddressAvailabilityResponse(boolean available, String reason) {

    public static AddressAvailabilityResponse from(AddressCheck check) {
        return new AddressAvailabilityResponse(check.available(),
                check.reason() == null ? null : check.reason().name());
    }

}
