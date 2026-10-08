package com.nhnacademy.blog.global.auth;

import java.time.Instant;

public record IssuedToken(String value, String id, Instant expiresAt) {
}
