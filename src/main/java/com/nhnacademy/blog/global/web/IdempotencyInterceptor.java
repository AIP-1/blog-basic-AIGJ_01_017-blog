package com.nhnacademy.blog.global.web;

import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 연타 방지 (T016, R-09). @Idempotent API에서 같은 회원·같은 경로·같은 Idempotency-Key의 두 번째 요청에는
 * 처음 응답을 그대로 돌려준다. 키와 응답은 Redis에 짧은 TTL로 둔다.
 * <ul>
 *   <li>키가 없거나 UUID가 아니면 400 IDEMPOTENCY_KEY_REQUIRED.</li>
 *   <li>처음 요청이 실패(2xx가 아님)하면 키를 지워 같은 키로 다시 시도할 수 있다.</li>
 *   <li>처음 요청이 아직 처리 중이면 잠시 기다렸다가 그 응답을 준다. 너무 오래 걸리면 429.</li>
 * </ul>
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Idempotency-Key";

    private static final String KEY_PREFIX = "idempotency:";
    private static final String PENDING = "PENDING";
    private static final String ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".key";
    private static final Duration WAIT_LIMIT = Duration.ofSeconds(3);
    private static final long WAIT_STEP_MILLIS = 100;

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final IdempotencyProperties properties;

    public IdempotencyInterceptor(StringRedisTemplate redis, JsonMapper jsonMapper,
                                  IdempotencyProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(Idempotent.class)) {
            return true;
        }
        String redisKey = redisKey(request, readKey(request));
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey, PENDING, properties.ttl()))) {
            request.setAttribute(ATTRIBUTE, redisKey);
            return true;
        }
        replay(response, waitForFirstResponse(redisKey));
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        String redisKey = (String) request.getAttribute(ATTRIBUTE);
        if (redisKey == null) {
            return;
        }
        ContentCachingResponseWrapper cached = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        boolean success = ex == null && response.getStatus() >= 200 && response.getStatus() < 300;
        if (!success || cached == null) {
            redis.delete(redisKey);
            return;
        }
        StoredResponse stored = new StoredResponse(response.getStatus(), response.getContentType(),
                response.getHeader(HttpHeaders.LOCATION),
                new String(cached.getContentAsByteArray(), StandardCharsets.UTF_8));
        redis.opsForValue().set(redisKey, jsonMapper.writeValueAsString(stored), properties.ttl());
    }

    private String readKey(HttpServletRequest request) {
        String key = request.getHeader(HEADER);
        try {
            return UUID.fromString(key == null ? "" : key.strip()).toString();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    /** 키는 회원(비회원은 anonymous)과 요청마다 따로다. 다른 사람이 같은 키를 보내도 남의 응답을 받지 않는다. */
    private String redisKey(HttpServletRequest request, String key) {
        Long memberId = LoginMembers.currentId();
        return KEY_PREFIX + (memberId == null ? "anonymous" : memberId) + ":" + request.getMethod() + ":"
                + request.getRequestURI() + ":" + key;
    }

    private StoredResponse waitForFirstResponse(String redisKey) {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            String value = redis.opsForValue().get(redisKey);
            if (value != null && !PENDING.equals(value)) {
                return jsonMapper.readValue(value, StoredResponse.class);
            }
            if (value == null) {
                // 처음 요청이 실패해 키가 지워졌다. 이 요청도 다시 보내게 한다
                break;
            }
            sleep();
        }
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, Map.of("retryAfterSeconds", 1));
    }

    private void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        if (stored.location() != null) {
            response.setHeader(HttpHeaders.LOCATION, stored.location());
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(stored.body());
    }

    private void sleep() {
        try {
            Thread.sleep(WAIT_STEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, Map.of("retryAfterSeconds", 1));
        }
    }

    record StoredResponse(int status, String contentType, String location, String body) {
    }

}
