package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.RetryAfterDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 비밀번호·인증 코드 시도 제한 (T055a, R-17). 로그인(이메일별), 인증 코드(이메일별), 비밀번호 변경(회원별)이 쓴다.
 * <ul>
 *   <li>대상별: 15분 안에 5번 틀리면 다섯 번째로 틀린 때부터 15분 동안 429. 맞히면 횟수를 지운다.</li>
 *   <li>IP별: 같은 IP에서 모든 대상을 합쳐 15분 안에 20번 틀리면 15분 동안 429. 비밀번호 하나를 여러 이메일에
 *       돌려 보는 공격을 막는다. 맞혀도 지우지 않는다(내 계정으로 한 번 로그인해 IP 횟수를 지우는 우회를 막으려고).</li>
 * </ul>
 * 막힌 동안은 맞는 값도 받지 않는다.
 * <p>
 * 시도 수는 Redis 키(attempt:{종류}:{대상}, attempt:ip:{주소})에 둔다. 확인하기 <b>전에</b> INCR로 먼저 하나 올려 자리를 잡는다.
 * "몇 번 틀렸나 보고 → 확인"으로 하면 동시에 보낸 요청 수십 개가 모두 "4번"을 보고 통과해 제한이 뚫린다.
 * INCR은 한 명령이라 동시에 와도 1, 2, 3 … 서로 다른 번호를 받는다. 그래서 확인은 제한 횟수까지만 된다.
 * 확인하지 않았거나(막힘) 틀린 것이 아닌 실패(정지 회원, 만료된 코드 등)면 올린 수를 되돌린다.
 */
@Component
public class AttemptLimiter {

    private static final String PREFIX = "attempt:";

    private final StringRedisTemplate redis;
    private final AttemptLimitProperties properties;

    public AttemptLimiter(StringRedisTemplate redis, AttemptLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * check를 한 번 실행한다. check가 던진 BusinessException 중 isFailure에 맞는 것은 "틀림"으로 센다.
     * 대상이나 IP 중 하나라도 막혀 있으면 check를 실행하지 않고 429(retryAfterSeconds)다.
     *
     * @param key 종류와 대상. 예: "login:a@b.com", "password-change:7"
     */
    public <T> T attempt(String key, Supplier<T> check, Predicate<BusinessException> isFailure) {
        List<Counter> counters = new ArrayList<>();
        counters.add(reserve(PREFIX + key, properties.maxFailures(), true));
        String ip = clientIp();
        if (ip != null) {
            counters.add(reserve(PREFIX + "ip:" + ip, properties.ipMaxFailures(), false));
        }

        List<Counter> locked = counters.stream().filter(Counter::overLimit).toList();
        if (!locked.isEmpty()) {
            // 확인하지 않으므로 이번 시도는 어느 쪽에도 세지 않는다
            counters.forEach(this::release);
            throw locked(locked);
        }
        try {
            T result = check.get();
            counters.forEach(counter -> {
                if (counter.resetOnSuccess()) {
                    redis.delete(counter.key());
                } else {
                    release(counter);
                }
            });
            return result;
        } catch (BusinessException e) {
            if (isFailure.test(e)) {
                // 이번이 제한 횟수째로 틀린 것이면 지금부터 15분 동안 막는다
                counters.stream()
                        .filter(counter -> counter.count() == counter.max())
                        .forEach(counter -> redis.expire(counter.key(), properties.window()));
            } else {
                counters.forEach(this::release);
            }
            throw e;
        } catch (RuntimeException e) {
            counters.forEach(this::release);
            throw e;
        }
    }

    /** 하나 올리고, 처음이거나 INCR 뒤 수명을 못 붙이고 끊겼던 키(수명 없음)면 15분 수명을 붙인다. */
    private Counter reserve(String key, int max, boolean resetOnSuccess) {
        Long reserved = redis.opsForValue().increment(key);
        long count = reserved == null ? 1 : reserved;
        Long ttl = redis.getExpire(key);
        if (count == 1 || ttl == null || ttl < 0) {
            redis.expire(key, properties.window());
        }
        return new Counter(key, max, resetOnSuccess, count);
    }

    private void release(Counter counter) {
        redis.opsForValue().decrement(counter.key());
    }

    /** 막힌 것들 중 가장 오래 남은 시간. */
    private BusinessException locked(List<Counter> locked) {
        long seconds = locked.stream()
                .map(counter -> redis.getExpire(counter.key()))
                .mapToLong(ttl -> ttl == null ? 1 : ttl)
                .max()
                .orElse(1);
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, new RetryAfterDetail(Math.max(seconds, 1)));
    }

    /**
     * 지금 요청의 접속 주소. 요청 밖(스케줄러 등)에서 부르면 null이라 IP 제한을 건너뛴다.
     * 프록시 뒤에 두면 프록시 주소가 되므로 server.forward-headers-strategy를 설정해야 한다(research R-17).
     */
    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    private record Counter(String key, int max, boolean resetOnSuccess, long count) {

        boolean overLimit() {
            return count > max;
        }

    }

}
