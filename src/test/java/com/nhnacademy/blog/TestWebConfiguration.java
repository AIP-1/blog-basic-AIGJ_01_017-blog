package com.nhnacademy.blog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * 통합 테스트의 MockMvc 요청마다 다른 접속 주소(10.x.x.x)를 준다.
 * 기본값이면 모든 요청이 127.0.0.1이라, 테스트들이 남긴 로그인 실패가 IP별 시도 제한(R-17)에 쌓여 뒤 테스트가 429를 받는다.
 * 주소를 정해야 하는 테스트는 요청에 .with(request -> { request.setRemoteAddr(...); ... })를 붙인다(그쪽이 나중에 적용된다).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestWebConfiguration {

    @Bean
    MockMvcBuilderCustomizer randomRemoteAddress() {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            request.setRemoteAddr("10.%d.%d.%d".formatted(random.nextInt(256), random.nextInt(256),
                    random.nextInt(1, 255)));
            return request;
        }));
    }

}
