package com.nhnacademy.blog.support;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.Idempotent;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 전용: 연타 방지 확인용. 실제로 실행된 횟수를 센다.
 */
@RestController
public class TestIdempotentController {

    public final AtomicInteger created = new AtomicInteger();
    public final AtomicInteger failed = new AtomicInteger();

    @Idempotent
    @PostMapping("/api/test/idempotent")
    public ResponseEntity<Map<String, Integer>> create(@RequestParam(defaultValue = "0") long sleepMillis)
            throws InterruptedException {
        Thread.sleep(sleepMillis);
        int id = created.incrementAndGet();
        return ResponseEntity.created(URI.create("/api/test/idempotent/" + id)).body(Map.of("id", id));
    }

    @Idempotent
    @PostMapping("/api/test/idempotent-fail")
    public Map<String, Integer> fail() {
        failed.incrementAndGet();
        throw new BusinessException(ErrorCode.NAME_TAKEN);
    }

}
