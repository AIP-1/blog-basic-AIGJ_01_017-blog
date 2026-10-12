package com.nhnacademy.blog.global.host;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogAddressRule;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 요청이 어느 블로그를 가리키는지 정하고, 블로그·플랫폼 주소를 만든다 (T009, R-04, 스텝 21).
 * <ul>
 *   <li>화면 주소: 블로그는 {@code /@{address}/…} 경로다(예: blog.com/@alpha/12). 서브도메인은 쓰지 않는다
 *       (배포 서버에 와일드카드 DNS가 없어서, 2026-10-12 지원 결정).</li>
 *   <li>API: 주소는 그대로 두고, 블로그 화면이 부를 때 {@link #HEADER} 헤더로 블로그를 알린다. 헤더가 없으면 플랫폼이다.</li>
 * </ul>
 */
@Component
public class BlogAddressResolver {

    /** 블로그 화면이 블로그 API를 부를 때 붙이는 헤더. 값은 블로그 주소(alpha). */
    public static final String HEADER = "X-Blog-Address";
    /** 화면 주소에서 블로그 주소 앞에 붙는 글자: /@alpha. */
    public static final String PATH_PREFIX = "/@";

    private final DomainProperties domainProperties;
    private final BlogRepository blogRepository;

    public BlogAddressResolver(DomainProperties domainProperties, BlogRepository blogRepository) {
        this.domainProperties = domainProperties;
        this.blogRepository = blogRepository;
    }

    public String platform() {
        return domainProperties.platform();
    }

    /** API 요청이 가리키는 곳. X-Blog-Address가 없으면 플랫폼이다. */
    public RequestBlog resolve(HttpServletRequest request) {
        String address = request.getHeader(HEADER);
        return address == null ? new RequestBlog.Platform() : resolveAddress(address);
    }

    /** 블로그 주소 값을 규칙으로 확인한다. 대소문자는 가리지 않는다. */
    public RequestBlog resolveAddress(String rawAddress) {
        String address = rawAddress.trim().toLowerCase(Locale.ROOT);
        return BlogAddressRule.isUsable(address) ? new RequestBlog.BlogAddress(address) : new RequestBlog.Unknown();
    }

    /** API 요청이 가리키는 블로그. 플랫폼·규칙에 맞지 않는 주소면 빈 값. 삭제된 블로그도 나오므로 볼 수 있는지는 따로 판단한다. */
    public Optional<Blog> findBlog(HttpServletRequest request) {
        return resolve(request) instanceof RequestBlog.BlogAddress(String address)
                ? blogRepository.findByAddress(address) : Optional.empty();
    }

    /** 화면 주소의 블로그 주소(/@alpha/… 의 alpha)로 찾는다. */
    public Optional<Blog> findBlog(String rawAddress) {
        return resolveAddress(rawAddress) instanceof RequestBlog.BlogAddress(String address)
                ? blogRepository.findByAddress(address) : Optional.empty();
    }

    /** 블로그 화면의 경로. path가 "/"면 블로그 첫 화면(/@alpha), 아니면 /@alpha/12 꼴. */
    public static String blogPath(String address, String pathAndQuery) {
        return PATH_PREFIX + address + ("/".equals(pathAndQuery) ? "" : pathAndQuery);
    }

    /** 같은 경로를 다른 블로그 주소로 보낼 URL (이사·다른 블로그 소속 글의 301, 알림의 링크, 공유 미리보기). */
    public String blogUrl(HttpServletRequest request, Blog blog, String pathAndQuery) {
        return platformUrl(request, blogPath(blog.getAddress(), pathAndQuery));
    }

    /** 플랫폼 주소의 URL (알림의 링크 중 블로그가 아닌 곳, 메일 링크, 소셜 로그인). */
    public String platformUrl(HttpServletRequest request, String pathAndQuery) {
        return url(request, platform(), pathAndQuery);
    }

    /** 지금 요청과 같은 프로토콜·포트로 플랫폼 호스트의 URL을 만든다(개발 :8080, :5173도 그대로). */
    private static String url(HttpServletRequest request, String host, String pathAndQuery) {
        StringBuilder url = new StringBuilder()
                .append(request.getScheme()).append("://")
                .append(host);
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        if (!defaultPort && port > 0) {
            url.append(':').append(port);
        }
        return url.append(pathAndQuery).toString();
    }

}
