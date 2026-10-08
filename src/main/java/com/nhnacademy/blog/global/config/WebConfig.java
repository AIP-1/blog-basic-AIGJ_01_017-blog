package com.nhnacademy.blog.global.config;

import com.nhnacademy.blog.global.host.CurrentBlogArgumentResolver;
import com.nhnacademy.blog.global.web.IdempotencyInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentBlogArgumentResolver currentBlogArgumentResolver;
    private final IdempotencyInterceptor idempotencyInterceptor;

    public WebConfig(CurrentBlogArgumentResolver currentBlogArgumentResolver,
                     IdempotencyInterceptor idempotencyInterceptor) {
        this.currentBlogArgumentResolver = currentBlogArgumentResolver;
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentBlogArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor).addPathPatterns("/api/**");
    }

}
