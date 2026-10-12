package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.SocialProvider;

/**
 * 제공사 하나의 OAuth 2.0 인가 코드 흐름 (T099).
 * 1) authorizeUrl: 사용자를 제공사 로그인 화면으로 보낼 주소. 2) fetchUserId: 돌아온 code를 토큰으로 바꾸고 제공사 회원 번호를 읽는다.
 */
public interface OAuthClient {

    SocialProvider provider();

    /** 앱 키가 설정되어 있나. */
    boolean configured();

    /** reauth면 이미 제공사에 로그인해 있어도 다시 확인하게 한다(탈퇴 본인 확인). */
    String authorizeUrl(String state, boolean reauth);

    /** code → 제공사 회원 번호. 실패하면 예외. */
    String fetchUserId(String code);

}
