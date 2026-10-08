package com.nhnacademy.blog.post.domain;

/**
 * 글 본문 세 가지 모양. 같은 본문에서 만들어지므로 함께 다닌다(문자열 셋을 따로 넘기다 순서가 바뀌는 실수를 막는다).
 *
 * @param html    서버 정화(HtmlSanitizer)를 거친 본문 HTML. 화면에 그린다
 * @param text    html에서 태그를 뺀 글자. 블로그 안 검색(SRCH-01, research R-16)이 본다
 * @param summary text를 300자로 줄인 요약. 목록에 보인다
 */
public record PostBody(String html, String text, String summary) {
}
