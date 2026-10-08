package com.nhnacademy.blog.global.config;

import com.nhnacademy.blog.global.host.CurrentBlogArgumentResolver;
import com.nhnacademy.blog.global.web.IdempotencyInterceptor;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentBlogArgumentResolver currentBlogArgumentResolver;
    private final IdempotencyInterceptor idempotencyInterceptor;
    private final UploadProperties uploadProperties;

    public WebConfig(CurrentBlogArgumentResolver currentBlogArgumentResolver,
                     IdempotencyInterceptor idempotencyInterceptor, UploadProperties uploadProperties) {
        this.currentBlogArgumentResolver = currentBlogArgumentResolver;
        this.idempotencyInterceptor = idempotencyInterceptor;
        this.uploadProperties = uploadProperties;
    }

    /**
     * 올린 이미지 내보내기: /uploads/{파일명} → 업로드 폴더의 파일 (T036).
     * 파일 이름이 UUID라 내용이 바뀌지 않으므로 오래 캐시해도 된다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadProperties.dir().toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
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
