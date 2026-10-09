package com.nhnacademy.blog.global.config;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.BlogHostResolver;
import com.nhnacademy.blog.global.host.RequestHost;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import com.nhnacademy.blog.global.visibility.PostAccess;
import com.nhnacademy.blog.global.visibility.PostVisibilityPolicy;
import com.nhnacademy.blog.post.application.PostPreview;
import com.nhnacademy.blog.post.application.PostPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.HtmlUtils;

/**
 * 화면 주소 처리 (T010, 원본 6장 ①, contracts/rest-api.md 화면 주소 단계).
 * /api/ 밖의 화면 주소는 서버가 블로그와 글을 먼저 확인하고 React 앱(index.html)을 준다.
 * 없거나 볼 수 없으면 404 상태로, 이사했거나 다른 블로그 소속 글이면 서버가 직접 301로 보낸다(검색엔진도 따라간다).
 * 확장자가 있는 파일과 /api, /uploads, /assets는 여기로 오지 않는다.
 * 비회원도 볼 수 있는 글의 주소면 index.html의 head에 공유 미리보기(Open Graph) 태그를 넣는다 (T072).
 * 미리보기를 가져가는 서버는 자바스크립트를 실행하지 않아서, React가 그린 내용이 아니라 이 HTML만 본다.
 */
@Controller
public class SpaForwardController {

    private static final String FIRST = "{s1:(?!api$|uploads$|assets$)[^.]+}";
    private static final Pattern POST_PATH = Pattern.compile("^/(\\d{1,18})$");
    private static final Resource INDEX = new ClassPathResource("static/index.html");
    /** 헤더에 글자 인코딩을 밝힌다. 미리보기를 가져가는 서버는 HTML 안의 meta charset보다 헤더를 먼저 믿기도 한다. */
    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final String NOT_BUILT = "<!doctype html><html><head><meta charset=\"utf-8\"><title>블로그</title></head>"
            + "<body><p>프론트 빌드가 없습니다. <code>./scripts/build-frontend.sh</code>를 실행하거나 "
            + "<code>cd frontend &amp;&amp; npm run dev</code>로 개발 서버를 쓰세요.</p></body></html>";

    private final BlogHostResolver blogHostResolver;
    private final BlogVisibilityPolicy blogVisibilityPolicy;
    private final PostVisibilityPolicy postVisibilityPolicy;
    private final PostPreviewService postPreviewService;

    public SpaForwardController(BlogHostResolver blogHostResolver, BlogVisibilityPolicy blogVisibilityPolicy,
                                PostVisibilityPolicy postVisibilityPolicy, PostPreviewService postPreviewService) {
        this.blogHostResolver = blogHostResolver;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
        this.postVisibilityPolicy = postVisibilityPolicy;
        this.postPreviewService = postPreviewService;
    }

    @GetMapping({
            "/",
            "/" + FIRST,
            "/" + FIRST + "/{s2:[^.]+}",
            "/" + FIRST + "/{s2:[^.]+}/{s3:[^.]+}",
            "/" + FIRST + "/{s2:[^.]+}/{s3:[^.]+}/{s4:[^.]+}",
            "/" + FIRST + "/{s2:[^.]+}/{s3:[^.]+}/{s4:[^.]+}/{s5:[^.]+}",
            // 태그 이름에는 점이 들어갈 수 있다(node.js). 확장자로 보지 않는다 (TAG-02)
            "/tag/{name:.+}"})
    public ResponseEntity<Resource> page(HttpServletRequest request) {
        return switch (blogHostResolver.resolve(request)) {
            case RequestHost.Platform platform -> app(HttpStatus.OK);
            // 플랫폼의 블로그 주소 꼴인데 규칙에 맞지 않거나 예약어면 없는 블로그다
            case RequestHost.Unknown unknown -> isUnderPlatform(request) ? app(HttpStatus.NOT_FOUND) : app(HttpStatus.OK);
            case RequestHost.BlogAddress blogAddress -> blogPage(request);
        };
    }

    private ResponseEntity<Resource> blogPage(HttpServletRequest request) {
        Optional<Blog> found = blogHostResolver.findBlog(request);
        if (found.isEmpty() || found.get().isDeleted()) {
            return app(HttpStatus.NOT_FOUND);
        }
        Blog blog = found.get();
        Long viewerId = LoginMembers.currentId();
        String path = request.getRequestURI();

        // 글 주소는 글이 어느 블로그에 있는지로 판단한다. 이사 전 블로그에 남은 글은 옛 주소에서 그대로 보인다
        var postPath = POST_PATH.matcher(path);
        if (postPath.matches()) {
            Long postId = Long.valueOf(postPath.group(1));
            return switch (postVisibilityPolicy.decide(postId, blog, viewerId)) {
                case PostAccess.MovedTo movedTo -> redirect(request, movedTo.blog());
                case PostAccess.NotFound notFound -> app(HttpStatus.NOT_FOUND);
                // 구독 안내는 화면이 API의 403 SUBSCRIBERS_ONLY로 그린다
                case PostAccess.Owner owner -> postPage(request, blog, postId);
                case PostAccess.Visible visible -> postPage(request, blog, postId);
                case PostAccess.SubscribersOnly subscribersOnly -> app(HttpStatus.OK);
            };
        }

        if (!blogVisibilityPolicy.canView(blog, viewerId)) {
            return app(HttpStatus.NOT_FOUND);
        }
        if (blog.isMoved()) {
            // 주인은 옛 블로그 관리 화면을 계속 쓴다 (BLOG-06)
            if (isManagePath(path) && blog.isOwnedBy(viewerId)) {
                return app(HttpStatus.OK);
            }
            Blog target = blog.getMovedToBlog();
            // 이사 간 블로그가 삭제되면 리다이렉트도 끊기고 없는 블로그다 (R-08)
            return target.isDeleted() ? app(HttpStatus.NOT_FOUND) : redirect(request, target);
        }
        return app(HttpStatus.OK);
    }

    /** 글 화면. 비회원도 볼 수 있는 글이면 공유 미리보기 태그를 넣은 index.html, 아니면 그대로. */
    private ResponseEntity<Resource> postPage(HttpServletRequest request, Blog blog, Long postId) {
        return postPreviewService.preview(blog, postId)
                .map(preview -> html(HttpStatus.OK, withPreview(indexHtml(), preview, request, blog)))
                .orElseGet(() -> app(HttpStatus.OK));
    }

    /**
     * head 끝에 Open Graph 태그를 넣는다. 값은 사용자가 쓴 글자(제목·요약·블로그 이름)라 HTML로 이스케이프한다
     * (제목에 "><script>를 넣어 태그를 깨는 공격 막기, XSS). og:url·og:image는 절대 주소여야 한다.
     */
    private String withPreview(String html, PostPreview preview, HttpServletRequest request, Blog blog) {
        String title = preview.title() + " - " + preview.blogName();
        StringBuilder tags = new StringBuilder()
                .append(meta("og:type", "article"))
                .append(meta("og:site_name", preview.blogName()))
                .append(meta("og:title", preview.title()))
                .append(meta("og:url", blogHostResolver.blogUrl(request, blog, "/" + preview.postId())));
        if (preview.description() != null && !preview.description().isBlank()) {
            tags.append(meta("og:description", preview.description()))
                    .append("<meta name=\"description\" content=\"").append(HtmlUtils.htmlEscape(preview.description()))
                    .append("\">\n");
        }
        if (preview.imagePath() != null) {
            tags.append(meta("og:image", blogHostResolver.blogUrl(request, blog, preview.imagePath())));
        }
        tags.append("<meta name=\"twitter:card\" content=\"")
                .append(preview.imagePath() != null ? "summary_large_image" : "summary").append("\">\n");
        String titleTag = "<title>" + HtmlUtils.htmlEscape(title) + "</title>";
        return html.replaceFirst("<title>[^<]*</title>", Matcher.quoteReplacement(titleTag))
                .replace("</head>", tags + "</head>");
    }

    private static String meta(String property, String content) {
        return "<meta property=\"" + property + "\" content=\"" + HtmlUtils.htmlEscape(content) + "\">\n";
    }

    private String indexHtml() {
        if (!INDEX.exists()) {
            return NOT_BUILT;
        }
        try (var input = INDEX.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ResponseEntity<Resource> html(HttpStatus status, String html) {
        return ResponseEntity.status(status)
                .contentType(HTML_UTF8)
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)));
    }

    private ResponseEntity<Resource> redirect(HttpServletRequest request, Blog target) {
        String query = request.getQueryString();
        String pathAndQuery = request.getRequestURI() + (query == null ? "" : "?" + query);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(blogHostResolver.blogUrl(request, target, pathAndQuery)))
                .build();
    }

    private ResponseEntity<Resource> app(HttpStatus status) {
        return ResponseEntity.status(status)
                .contentType(HTML_UTF8)
                .cacheControl(CacheControl.noCache())
                .body(INDEX.exists() ? INDEX : notBuilt());
    }

    /** 프론트를 빌드하지 않았을 때(개발 중 백엔드만 띄움) 보여 줄 안내. */
    private Resource notBuilt() {
        return new ByteArrayResource(NOT_BUILT.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isUnderPlatform(HttpServletRequest request) {
        return request.getServerName() != null
                && request.getServerName().toLowerCase(Locale.ROOT).endsWith("." + blogHostResolver.platform());
    }

    private boolean isManagePath(String path) {
        return path.equals("/manage") || path.startsWith("/manage/");
    }

}
