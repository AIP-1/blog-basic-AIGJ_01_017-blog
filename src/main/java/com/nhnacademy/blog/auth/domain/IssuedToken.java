package com.nhnacademy.blog.auth.domain;

import java.time.Instant;

public record IssuedToken(String value, String id, Instant expiresAt) {
}
