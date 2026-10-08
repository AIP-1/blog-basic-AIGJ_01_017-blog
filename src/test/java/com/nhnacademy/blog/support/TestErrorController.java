package com.nhnacademy.blog.support;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 전용: 여러 가지 오류를 일으켜 오류 응답 모양을 확인한다.
 */
@RestController
public class TestErrorController {

    public record TitleRequest(@NotBlank(message = "제목을 입력해 주세요.") String title) {
    }

    @PostMapping("/api/test/errors/validate")
    public String validate(@Valid @RequestBody TitleRequest request) {
        return "ok";
    }

    @GetMapping("/api/test/errors/posts/{id}")
    public String post(@PathVariable Long id) {
        return "ok";
    }

    @GetMapping("/api/test/errors/suspended")
    public String suspended() {
        throw new BusinessException(ErrorCode.MEMBER_SUSPENDED, Map.of("reason", "SPAM"));
    }

    @GetMapping("/api/test/errors/conflict")
    public String conflict() {
        throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
    }

    @GetMapping("/api/test/errors/boom")
    public String boom() {
        throw new IllegalStateException("select * from secret-table");
    }

}
