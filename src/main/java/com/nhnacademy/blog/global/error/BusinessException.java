package com.nhnacademy.blog.global.error;

import java.util.List;

/**
 * 서비스에서 정해진 오류 코드로 응답을 끝낼 때 던진다. GlobalExceptionHandler가 ErrorResponse로 바꾼다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldErrorDetail> fieldErrors;
    private final transient Object detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of(), null);
    }

    public BusinessException(ErrorCode errorCode, Object detail) {
        this(errorCode, List.of(), detail);
    }

    private BusinessException(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors, Object detail) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.fieldErrors = List.copyOf(fieldErrors);
        this.detail = detail;
    }

    /** 입력 오류. 어느 항목이 왜 잘못됐는지 함께 준다. */
    public static BusinessException fieldErrors(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        return new BusinessException(errorCode, fieldErrors, null);
    }

    public static BusinessException invalidField(String field, String reason) {
        return fieldErrors(ErrorCode.VALIDATION_FAILED, List.of(new FieldErrorDetail(field, reason)));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }

    public Object getDetail() {
        return detail;
    }

}
