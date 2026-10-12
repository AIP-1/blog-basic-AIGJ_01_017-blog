package com.nhnacademy.blog.global.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nhnacademy.blog.blog.domain.Blog;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 블로그 주소 해석 (R-04, 스텝 21). API는 X-Blog-Address 헤더, 화면은 /@주소 경로. */
class BlogAddressResolverTest {

    private final BlogAddressResolver resolver = new BlogAddressResolver(new DomainProperties("blog.com"), null);

    @Test
    void withoutHeaderItIsThePlatformWhateverTheHost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("alpha.blog.com");
        assertThat(resolver.resolve(request)).isInstanceOf(RequestBlog.Platform.class);
    }

    @Test
    void headerNamesTheBlog() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BlogAddressResolver.HEADER, "My-Blog");
        assertThat(resolver.resolve(request)).isEqualTo(new RequestBlog.BlogAddress("my-blog"));
    }

    @Test
    void addressesBreakingTheRuleOrReservedAreUnknown() {
        assertThat(resolver.resolveAddress("abc")).isInstanceOf(RequestBlog.Unknown.class);
        assertThat(resolver.resolveAddress("admin")).isInstanceOf(RequestBlog.Unknown.class);
        assertThat(resolver.resolveAddress("a.b")).isInstanceOf(RequestBlog.Unknown.class);
        assertThat(resolver.resolveAddress("alpha")).isEqualTo(new RequestBlog.BlogAddress("alpha"));
    }

    @Test
    void blogPathPutsTheAddressAfterAt() {
        assertThat(BlogAddressResolver.blogPath("alpha", "/")).isEqualTo("/@alpha");
        assertThat(BlogAddressResolver.blogPath("alpha", "/12")).isEqualTo("/@alpha/12");
        assertThat(BlogAddressResolver.blogPath("alpha", "/category/3?page=2")).isEqualTo("/@alpha/category/3?page=2");
    }

    @Test
    void blogUrlKeepsSchemePortAndPathOnThePlatformHost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerPort(8080);
        Blog blog = mock(Blog.class);
        when(blog.getAddress()).thenReturn("gamma");

        assertThat(resolver.blogUrl(request, blog, "/15?x=1")).isEqualTo("http://blog.com:8080/@gamma/15?x=1");

        request.setScheme("https");
        request.setServerPort(443);
        assertThat(resolver.blogUrl(request, blog, "/")).isEqualTo("https://blog.com/@gamma");
    }

}
