package com.nhnacademy.blog.global.host;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogAddressRule;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 요청 Host 헤더로 플랫폼 주소인지, 어느 블로그 주소인지 정한다 (T009, R-04).
 */
@Component
public class BlogHostResolver {

    private final DomainProperties domainProperties;
    private final BlogRepository blogRepository;

    public BlogHostResolver(DomainProperties domainProperties, BlogRepository blogRepository) {
        this.domainProperties = domainProperties;
        this.blogRepository = blogRepository;
    }

    public String platform() {
        return domainProperties.platform();
    }

    public RequestHost resolve(HttpServletRequest request) {
        return resolve(request.getServerName());
    }

    public RequestHost resolve(String serverName) {
        if (serverName == null) {
            return new RequestHost.Unknown();
        }
        String host = serverName.toLowerCase(Locale.ROOT);
        String platform = domainProperties.platform();
        if (host.equals(platform) || host.equals("www." + platform)) {
            return new RequestHost.Platform();
        }
        String suffix = "." + platform;
        if (!host.endsWith(suffix)) {
            return new RequestHost.Unknown();
        }
        String address = host.substring(0, host.length() - suffix.length());
        if (!BlogAddressRule.isUsable(address)) {
            return new RequestHost.Unknown();
        }
        return new RequestHost.BlogAddress(address);
    }

    /** 요청 Host의 블로그. 플랫폼·알 수 없는 주소면 빈 값. 삭제된 블로그도 나오므로 볼 수 있는지는 따로 판단한다. */
    public Optional<Blog> findBlog(HttpServletRequest request) {
        if (resolve(request) instanceof RequestHost.BlogAddress(String address)) {
            return blogRepository.findByAddress(address);
        }
        return Optional.empty();
    }

    /** 같은 경로를 다른 블로그 주소로 보낼 URL (이사·다른 블로그 소속 글의 301, 알림의 링크). */
    public String blogUrl(HttpServletRequest request, Blog blog, String pathAndQuery) {
        return url(request, domainProperties.blogHost(blog.getAddress()), pathAndQuery);
    }

    /** 플랫폼 주소의 URL (알림의 링크 중 블로그가 아닌 곳, 예: 마이페이지). */
    public String platformUrl(HttpServletRequest request, String pathAndQuery) {
        return url(request, platform(), pathAndQuery);
    }

    /** 지금 요청과 같은 프로토콜·포트로 다른 호스트의 URL을 만든다(개발 :8080, :5173도 그대로). */
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
