package com.nhnacademy.blog.global.error;

/** 입력 오류 한 항목. reason은 화면에 그대로 띄워도 되는 문장이다. */
public record FieldErrorDetail(String field, String reason) {
}
