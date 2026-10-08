package com.nhnacademy.blog.auth.presentation;

import com.nhnacademy.blog.auth.application.EmailVerificationService;
import com.nhnacademy.blog.auth.presentation.dto.EmailCodeRequest;
import com.nhnacademy.blog.auth.presentation.dto.EmailVerificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 인증 (T017, OWN-01). 가입 화면에서 코드 받기·확인 버튼이 부른다.
 */
@RestController
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    /** 202: 요청을 받았고 메일은 따로 간다. 이미 가입된 이메일 409, 1분 안에 다시 요청 429. */
    @PostMapping("/api/auth/email-verifications")
    public ResponseEntity<Void> send(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.send(request.email());
        return ResponseEntity.accepted().build();
    }

    /** 화면 단계 확인. 맞으면 200, 틀리면 400 INVALID_VERIFICATION_CODE, 시간이 지났으면 400 VERIFICATION_EXPIRED. */
    @PostMapping("/api/auth/email-verifications/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody EmailCodeRequest request) {
        emailVerificationService.verify(request.email(), request.code());
        return ResponseEntity.ok().build();
    }

}
