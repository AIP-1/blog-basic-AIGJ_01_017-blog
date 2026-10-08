package com.nhnacademy.blog.global.host;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.visibility.BlogVisibilityPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentBlogArgumentResolver implements HandlerMethodArgumentResolver {

    private final BlogHostResolver blogHostResolver;
    private final BlogVisibilityPolicy blogVisibilityPolicy;

    public CurrentBlogArgumentResolver(BlogHostResolver blogHostResolver, BlogVisibilityPolicy blogVisibilityPolicy) {
        this.blogHostResolver = blogHostResolver;
        this.blogVisibilityPolicy = blogVisibilityPolicy;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentBlog.class) && Blog.class.equals(parameter.getParameterType());
    }

    @Override
    public Blog resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Long viewerId = LoginMembers.currentId();
        Blog blog = blogHostResolver.findBlog(request)
                .filter(found -> blogVisibilityPolicy.canView(found, viewerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // 이사한 블로그는 주인만 옛 주소의 API를 쓴다 (BLOG-06)
        if (blog.isMoved() && !blog.isOwnedBy(viewerId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return blog;
    }

}
