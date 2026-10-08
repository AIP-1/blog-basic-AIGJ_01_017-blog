package com.nhnacademy.blog.global.config;

import com.nhnacademy.blog.global.host.CurrentBlogArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentBlogArgumentResolver currentBlogArgumentResolver;

    public WebConfig(CurrentBlogArgumentResolver currentBlogArgumentResolver) {
        this.currentBlogArgumentResolver = currentBlogArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentBlogArgumentResolver);
    }

}
