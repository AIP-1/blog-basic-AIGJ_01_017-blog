package com.nhnacademy.blog.global.security;

import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * 글 본문 HTML을 허용 목록으로 정화한다 (R-05, 기능 명세 공통 규칙 보안).
 * 남는 것: 문단, 문단 제목, 굵게·기울임, 목록, 인용, 코드 블록, http/https 링크, 직접 올린 이미지.
 * 스크립트, 이벤트 속성, 인라인 스타일은 모두 지운다. 화면을 거치지 않은 요청도 같은 정화를 거친다.
 */
@Component
public class HtmlSanitizer {

    /** 링크는 http/https 절대 주소만. 상대 주소와 javascript: 등은 지운다. */
    private static final Pattern LINK_HREF = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    /** 이미지는 이 서비스에 올린 파일만 (/uploads/{uuid}.{ext}). */
    private static final Pattern UPLOADED_IMAGE_SRC =
            Pattern.compile("^/uploads/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|gif|webp)$", Pattern.CASE_INSENSITIVE);

    /** 코드 블록 언어 표시 (에디터가 붙이는 class="language-java"). */
    private static final Pattern CODE_LANGUAGE_CLASS = Pattern.compile("^language-[A-Za-z0-9+#_-]{1,30}$");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br")
            .allowElements("h1", "h2", "h3", "h4", "h5", "h6")
            .allowElements("strong", "b", "em", "i")
            .allowElements("ul", "ol", "li")
            .allowElements("blockquote")
            .allowElements("pre", "code")
            .allowAttributes("class").matching(CODE_LANGUAGE_CLASS).onElements("code")
            .allowElements("a")
            .allowUrlProtocols("http", "https")
            .allowAttributes("href").matching(LINK_HREF).onElements("a")
            .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
            .allowElements("img")
            .allowAttributes("src").matching(UPLOADED_IMAGE_SRC).onElements("img")
            .allowAttributes("alt").onElements("img")
            .toFactory();

    public String sanitize(String html) {
        if (html == null) {
            return "";
        }
        return POLICY.sanitize(html);
    }

}
