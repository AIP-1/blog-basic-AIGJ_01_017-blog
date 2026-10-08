package com.nhnacademy.blog.global.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 API 오류를 COM-02 모양(ErrorResponse)으로 바꾼다. 500에는 내부 정보를 담지 않고 로그에만 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus()).contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(e));
    }

    /** @Valid 본문, @ModelAttribute 검증 실패. MethodArgumentNotValidException도 여기로 온다. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e) {
        List<FieldErrorDetail> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(),
                        error.isBindingFailure() ? INVALID_FORMAT : error.getDefaultMessage()))
                .toList();
        return validation(fieldErrors);
    }

    /** @RequestParam, @PathVariable 등 메서드 인자 검증 실패. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        List<FieldErrorDetail> fieldErrors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validation(fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validation(List.of(new FieldErrorDetail(e.getName(), INVALID_FORMAT)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return validation(List.of(new FieldErrorDetail(e.getParameterName(), "필수 값입니다.")));
    }

    /** 본문이 JSON이 아니거나 형식이 맞지 않음. 파서 메시지는 내부 정보라 내보내지 않는다. */
    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return validation(List.of());
    }

    /** 업로드 크기 제한(spring.servlet.multipart.max-file-size)을 넘음. 컨트롤러에 오기 전에 난다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return error(ErrorCode.IMAGE_TOO_LARGE);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        return error(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return error(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return error(ErrorCode.UNAUTHORIZED);
    }

    /** 메서드 보안(@PreAuthorize)에 걸림. 비회원이면 로그인 필요(401), 회원이면 권한 없음(403). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
        return error(anonymous ? ErrorCode.UNAUTHORIZED : ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상하지 못한 오류", e);
        return error(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> validation(List<FieldErrorDetail> fieldErrors) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.validation(fieldErrors));
    }

    private ResponseEntity<ErrorResponse> error(ErrorCode errorCode) {
        // Accept가 text/html이어도(브라우저 주소창) 같은 JSON 본문을 준다
        return ResponseEntity.status(errorCode.getStatus()).contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(errorCode));
    }

}
