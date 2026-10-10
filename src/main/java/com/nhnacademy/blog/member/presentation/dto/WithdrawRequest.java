package com.nhnacademy.blog.member.presentation.dto;

/** 탈퇴 본인 확인 `{ password }` (이메일 가입 회원). */
public record WithdrawRequest(String password) {
}
