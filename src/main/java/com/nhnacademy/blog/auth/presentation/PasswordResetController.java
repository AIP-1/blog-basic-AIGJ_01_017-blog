package com.nhnacademy.blog.auth.presentation;

import com.nhnacademy.blog.auth.application.PasswordResetService;
import com.nhnacademy.blog.auth.presentation.dto.PasswordResetConfirmRequest;
import com.nhnacademy.blog.auth.presentation.dto.PasswordResetRequest;
import com.nhnacademy.blog.global.host.BlogHostResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 재설정 (T088, OWN-02). 로그인 화면의 "비밀번호를 잊었어요"가 연 /password-reset 화면이 부른다.
 */
@RestController
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final BlogHostResolver hostResolver;

    public PasswordResetController(PasswordResetService passwordResetService, BlogHostResolver hostResolver) {
        this.passwordResetService = passwordResetService;
        this.hostResolver = hostResolver;
    }

    /** 202: 가입하지 않은 이메일이어도 같은 응답이다(가입 여부를 드러내지 않음). 같은 이메일로 1분 안에 다시 요청하면 429. */
    @PostMapping("/api/auth/password-reset")
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request,
                                        HttpServletRequest httpRequest) {
        passwordResetService.request(request.email(), linkBase(hostResolver, httpRequest));
        return ResponseEntity.accepted().build();
    }

    /** 204. 틀렸거나 30분 지났거나 이미 쓴 링크면 400 RESET_TOKEN_INVALID. 바꾼 뒤에는 로그인 화면에서 새 비밀번호로 들어온다. */
    @PutMapping("/api/auth/password-reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** 메일 링크가 열 화면 주소. 플랫폼 주소에 지금 요청과 같은 프로토콜·포트로 만든다(개발 :8080·:5173도 그대로). */
    static String linkBase(BlogHostResolver hostResolver, HttpServletRequest request) {
        return hostResolver.platformUrl(request, "/password-reset?token=");
    }

}
