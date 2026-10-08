package com.nhnacademy.blog.global.web;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.FieldErrorDetail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 요청 본문을 컨트롤러 안에서 검증한다.
 * 파라미터에 @Valid를 붙이면 메서드에 들어오기 전에 검증되어, 주인 검사(403)보다 입력 오류(400)가 먼저 나간다.
 * 상태 코드 순서(404 → 401 → 403 → 400, contracts/rest-api.md)를 지키려고 주인 검사 뒤에 이것을 부른다.
 */
@Component
public class RequestValidator {

    private final Validator validator;

    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> T validate(T request) {
        List<FieldErrorDetail> errors = validator.validate(request).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(RequestValidator::toFieldError)
                .toList();
        if (!errors.isEmpty()) {
            throw BusinessException.fieldErrors(ErrorCode.VALIDATION_FAILED, errors);
        }
        return request;
    }

    private static FieldErrorDetail toFieldError(ConstraintViolation<?> violation) {
        return new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage());
    }

}
