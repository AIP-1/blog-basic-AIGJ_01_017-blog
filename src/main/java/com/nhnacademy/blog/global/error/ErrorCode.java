package com.nhnacademy.blog.global.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 (contracts/rest-api.md 오류 본문, COM-02). message는 화면에 그대로 띄워도 되는 문장이다.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "요청 키가 필요합니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않습니다."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다. 다시 요청해 주세요."),
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "재설정 링크가 올바르지 않거나 만료되었습니다."),
    BLOG_ADDRESS_INVALID(HttpStatus.BAD_REQUEST, "블로그 주소를 확인해 주세요."),
    UNSUPPORTED_IMAGE(HttpStatus.BAD_REQUEST, "jpg, png, gif, webp 이미지만 올릴 수 있습니다. 파일 이름의 확장자도 실제 형식과 같아야 합니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "10MB 이하 이미지만 올릴 수 있습니다."),
    TOO_MANY_TAGS(HttpStatus.BAD_REQUEST, "태그는 10개까지 달 수 있습니다."),
    BANNED_WORD(HttpStatus.BAD_REQUEST, "이 블로그에서 쓸 수 없는 단어가 들어 있습니다."),
    INVALID_MOVE_TARGET(HttpStatus.BAD_REQUEST, "이사할 수 없는 블로그입니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다."),

    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    CSRF_REJECTED(HttpStatus.FORBIDDEN, "허용되지 않은 요청입니다."),
    MEMBER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다."),
    BLOCKED_BY_BLOG(HttpStatus.FORBIDDEN, "이 블로그에서 차단되었습니다."),
    SUBSCRIBERS_ONLY(HttpStatus.FORBIDDEN, "구독자만 볼 수 있는 글입니다."),
    COMMENTS_DISABLED(HttpStatus.FORBIDDEN, "댓글을 쓸 수 없는 글입니다."),
    POST_BLINDED(HttpStatus.FORBIDDEN, "숨김 처리된 글은 수정할 수 없습니다."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 내용을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

    EMAIL_TAKEN(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 쓰고 있는 닉네임입니다."),
    BLOG_ADDRESS_TAKEN(HttpStatus.CONFLICT, "이미 쓰였거나 쓸 수 없는 블로그 주소입니다."),
    BLOG_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "블로그는 5개까지 만들 수 있습니다."),
    PRIMARY_BLOG(HttpStatus.CONFLICT, "대표 블로그는 삭제할 수 없습니다."),
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 카테고리가 있어 삭제할 수 없습니다."),
    CATEGORY_DEPTH(HttpStatus.CONFLICT, "카테고리는 2단계까지 만들 수 있습니다."),
    NAME_TAKEN(HttpStatus.CONFLICT, "이미 있는 이름입니다."),
    SOCIAL_ALREADY_LINKED(HttpStatus.CONFLICT, "다른 계정에 연결된 소셜 계정입니다."),
    PROVIDER_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 연결된 서비스입니다."),
    LAST_LOGIN_METHOD(HttpStatus.CONFLICT, "로그인 수단이 하나뿐이라 해제할 수 없습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고했습니다."),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "이미 차단한 회원입니다."),
    BANNED_WORD_LIMIT(HttpStatus.CONFLICT, "금칙어는 100개까지 등록할 수 있습니다."),
    RANKING_UPDATED(HttpStatus.CONFLICT, "랭킹이 새로 계산되었습니다. 처음부터 다시 불러와 주세요."),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

}
