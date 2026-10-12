package com.nhnacademy.blog.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 기기 쿠키의 값 (T055b, R-17, OWASP "device cookie"). 로그인에 성공한 브라우저에 "회원 번호.임의 값.서명"을 준다.
 * 서명은 HMAC-SHA256(서버 비밀 키)이라 회원 번호를 바꾸거나 값을 지어내면 맞지 않는다. 임의 값은 추측할 수 없다(SecureRandom 128비트).
 * 비밀 키는 JWT 비밀 키에 용도 이름("device-cookie:")을 붙여 따로 만든다(같은 키를 두 용도에 그대로 쓰지 않으려고).
 */
@Component
public class DeviceTokens {

    private static final String PURPOSE = "device-cookie:";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public DeviceTokens(AuthProperties properties) {
        this.key = (PURPOSE + properties.jwtSecret()).getBytes(StandardCharsets.UTF_8);
    }

    /** 새 기기 값. */
    public String issue(Long memberId) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String body = memberId + "." + ENCODER.encodeToString(nonce);
        return body + "." + sign(body);
    }

    /** 서명이 맞으면 그 기기(회원 번호, 임의 값). 형식이 틀리거나 서명이 다르면 빈 값. */
    public Optional<Device> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        String body = parts[0] + "." + parts[1];
        // 서명 비교는 걸린 시간으로 몇 글자가 맞았는지 알 수 없게 고정 시간 비교를 쓴다
        if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Device(Long.parseLong(parts[0]), parts[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 기기 하나: 이 브라우저에서 로그인에 성공한 회원과 기기의 임의 값. */
    public record Device(Long memberId, String nonce) {
    }

}
