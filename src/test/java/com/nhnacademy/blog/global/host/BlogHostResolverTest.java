package com.nhnacademy.blog.global.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nhnacademy.blog.blog.domain.Blog;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BlogHostResolverTest {

    private final BlogHostResolver resolver = new BlogHostResolver(new DomainProperties("blog.com"), null);

    @Test
    void platformHosts() {
        assertThat(resolver.resolve("blog.com")).isInstanceOf(RequestHost.Platform.class);
        assertThat(resolver.resolve("www.blog.com")).isInstanceOf(RequestHost.Platform.class);
        assertThat(resolver.resolve("BLOG.com")).isInstanceOf(RequestHost.Platform.class);
    }

    @Test
    void blogSubdomain() {
        assertThat(resolver.resolve("alpha.blog.com")).isEqualTo(new RequestHost.BlogAddress("alpha"));
        assertThat(resolver.resolve("My-Blog.blog.com")).isEqualTo(new RequestHost.BlogAddress("my-blog"));
    }

    @Test
    void unknownHosts() {
        assertThat(resolver.resolve("other.com")).isInstanceOf(RequestHost.Unknown.class);
        assertThat(resolver.resolve("evilblog.com")).isInstanceOf(RequestHost.Unknown.class);
        assertThat(resolver.resolve("a.b.blog.com")).isInstanceOf(RequestHost.Unknown.class);
        assertThat(resolver.resolve("abc.blog.com")).isInstanceOf(RequestHost.Unknown.class);
        assertThat(resolver.resolve("admin.blog.com")).isInstanceOf(RequestHost.Unknown.class);
        assertThat(resolver.resolve((String) null)).isInstanceOf(RequestHost.Unknown.class);
    }

    @Test
    void blogUrlKeepsSchemePortAndPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerPort(8080);
        Blog blog = mock(Blog.class);
        when(blog.getAddress()).thenReturn("gamma");

        assertThat(resolver.blogUrl(request, blog, "/15?x=1")).isEqualTo("http://gamma.blog.com:8080/15?x=1");

        request.setScheme("https");
        request.setServerPort(443);
        assertThat(resolver.blogUrl(request, blog, "/")).isEqualTo("https://gamma.blog.com/");
    }

}
