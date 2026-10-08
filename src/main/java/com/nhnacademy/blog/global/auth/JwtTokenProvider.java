package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.member.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Access·Refresh 토큰(JWT, HS256)을 만들고 검증한다 (R-03).
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_REMEMBER_ME = "rem";
    private static final int MIN_SECRET_BYTES = 32;

    private final AuthProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenProvider(AuthProperties properties, Clock clock) {
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.auth.jwt-secret은 32바이트 이상이어야 합니다.");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.properties = properties;
        this.clock = clock;
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public IssuedToken createAccessToken(Long memberId, Role role) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = baseClaims(memberId, now, now.plus(properties.accessTokenTtl()))
                .claim(CLAIM_TYPE, TokenType.ACCESS.name())
                .claim(CLAIM_ROLE, role.name());
        return encode(claims.build());
    }

    public IssuedToken createRefreshToken(Long memberId, boolean rememberMe) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = baseClaims(memberId, now, now.plus(properties.refreshTokenTtl()))
                .claim(CLAIM_TYPE, TokenType.REFRESH.name())
                .claim(CLAIM_REMEMBER_ME, rememberMe);
        return encode(claims.build());
    }

    /** 서명·만료·형식이 맞으면 내용을, 아니면 빈 값을 준다. */
    public Optional<TokenClaims> parse(String token, TokenType expectedType) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            TokenType type = TokenType.valueOf(jwt.getClaimAsString(CLAIM_TYPE));
            if (type != expectedType) {
                return Optional.empty();
            }
            Role role = type == TokenType.ACCESS ? Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE)) : null;
            boolean rememberMe = Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM_REMEMBER_ME));
            return Optional.of(new TokenClaims(type, Long.valueOf(jwt.getSubject()), role, jwt.getId(),
                    jwt.getExpiresAt(), rememberMe));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private JwtClaimsSet.Builder baseClaims(Long memberId, Instant issuedAt, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt);
    }

    private IssuedToken encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, claims.getId(), claims.getExpiresAt());
    }

}
