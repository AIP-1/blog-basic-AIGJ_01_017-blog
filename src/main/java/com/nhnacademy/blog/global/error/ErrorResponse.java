package com.nhnacademy.blog.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 오류 응답 본문 (COM-02). 스택, SQL, 클래스 이름 같은 내부 정보는 담지 않는다.
 * fieldErrors는 입력 오류에만, detail은 오류마다 더 줄 정보가 있을 때만 들어간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldErrorDetail> fieldErrors, Object detail) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, null);
    }

    public static ErrorResponse of(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        List<FieldErrorDetail> fieldErrors = e.getFieldErrors().isEmpty() ? null : e.getFieldErrors();
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors, e.getDetail());
    }

    public static ErrorResponse validation(List<FieldErrorDetail> fieldErrors) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors.isEmpty() ? null : fieldErrors,
                null);
    }

}
