package com.nhnacademy.blog.global.host;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 블로그 주소에서 부르는 API(B)에서 요청의 블로그를 받는다. 블로그 화면(/@주소)이 X-Blog-Address 헤더로 알린다(스텝 21).
 * 없거나, 볼 수 없거나, 이사한 블로그를 주인이 아닌 사람이 부르면 404다 (contracts/rest-api.md 주소와 Host 범위).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentBlog {
}
