package com.nhnacademy.blog.blog.domain;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 블로그 주소 규칙 (R-04). 영문 소문자·숫자·하이픈 4~32자, 처음과 끝은 하이픈이 아니다. 예약어는 쓸 수 없다.
 */
public final class BlogAddressRule {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{2,30}[a-z0-9]$");

    /** 플랫폼이 쓰거나 쓸 수 있는 이름. */
    private static final Set<String> RESERVED = Set.of(
            "www", "api", "admin", "static", "mail", "login", "logout", "signup", "manage", "assets", "uploads",
            "blog", "help", "support", "notice", "cdn", "smtp", "imap", "ftp", "test", "dev", "root", "system");

    private BlogAddressRule() {
    }

    public static boolean hasValidFormat(String address) {
        return address != null && FORMAT.matcher(address).matches();
    }

    public static boolean isReserved(String address) {
        return address != null && RESERVED.contains(address);
    }

    /** 블로그 주소로 쓸 수 있는가 (형식이 맞고 예약어가 아님). */
    public static boolean isUsable(String address) {
        return hasValidFormat(address) && !isReserved(address);
    }

}
