package com.nhnacademy.blog.global.auth;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.RetryAfterDetail;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 비밀번호·인증 코드 시도 제한 (T055a, R-17). 로그인(이메일별), 인증 코드(이메일별), 비밀번호 변경(회원별)이 쓴다.
 * 15분 안에 5번 틀리면 다섯 번째로 틀린 때부터 15분 동안 429 TOO_MANY_REQUESTS다. 막힌 동안은 맞는 값도 받지 않는다.
 * <p>
 * 시도 수는 Redis 키(attempt:{종류}:{대상})에 둔다. 확인하기 <b>전에</b> INCR로 먼저 하나 올려 자리를 잡는다.
 * "몇 번 틀렸나 보고 → 확인"으로 하면 동시에 보낸 요청 수십 개가 모두 "4번"을 보고 통과해 제한이 뚫린다.
 * INCR은 한 명령이라 동시에 와도 1, 2, 3 … 서로 다른 번호를 받는다. 그래서 확인은 최대 5번까지만 된다.
 * <ul>
 *   <li>맞으면 키를 지운다(틀린 횟수 초기화).</li>
 *   <li>틀리면 올린 수를 그대로 둔다. 다섯 번째로 틀렸으면 수명을 15분으로 다시 잡아 그때부터 15분 막는다.</li>
 *   <li>틀린 것이 아닌 실패(정지 회원, 만료된 코드 등)는 올린 수를 되돌린다.</li>
 * </ul>
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
     * 이미 막혀 있으면 check를 실행하지 않고 429(retryAfterSeconds)다.
     *
     * @param key 종류와 대상. 예: "login:a@b.com", "password-change:7"
     */
    public <T> T attempt(String key, Supplier<T> check, Predicate<BusinessException> isFailure) {
        String redisKey = PREFIX + key;
        Long reserved = redis.opsForValue().increment(redisKey);
        long count = reserved == null ? 1 : reserved;
        // 처음이거나, INCR 뒤 수명을 못 붙이고 끊겼던 키(수명 없음)면 15분 수명을 붙인다
        Long ttl = redis.getExpire(redisKey);
        if (count == 1 || ttl == null || ttl < 0) {
            redis.expire(redisKey, properties.window());
        }
        if (count > properties.maxFailures()) {
            throw locked(redisKey);
        }
        try {
            T result = check.get();
            redis.delete(redisKey);
            return result;
        } catch (BusinessException e) {
            if (isFailure.test(e)) {
                if (count == properties.maxFailures()) {
                    // 다섯 번째로 틀림: 지금부터 15분 동안 막는다
                    redis.expire(redisKey, properties.window());
                }
            } else {
                redis.opsForValue().decrement(redisKey);
            }
            throw e;
        } catch (RuntimeException e) {
            redis.opsForValue().decrement(redisKey);
            throw e;
        }
    }

    private BusinessException locked(String redisKey) {
        Long seconds = redis.getExpire(redisKey);
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                new RetryAfterDetail(seconds == null || seconds < 1 ? 1 : seconds));
    }

}
