package com.nhnacademy.blog.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** 헬스체크. 롤링 배포 스크립트가 새 앱이 요청을 받을 수 있는지 /actuator/health/readiness로 묻는다. */
class HealthIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    void readinessAndLivenessAreUpWithoutDetails() throws Exception {
        for (String path : new String[] {"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    // 바깥에도 열리는 주소라 무엇을 검사하는지(DB·Redis 이름, 주소)는 보이지 않는다
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
    }

    @Test
    void otherActuatorEndpointsAreNotOpen() throws Exception {
        // 설정값(env)·빈 목록(beans) 같은 것은 열지 않는다
        for (String path : new String[] {"/actuator", "/actuator/env", "/actuator/beans"}) {
            mockMvc.perform(get(path))
                    .andExpect(content().string(Matchers.not(Matchers.containsString("propertySources"))))
                    .andExpect(content().string(Matchers.not(Matchers.containsString("\"_links\""))))
                    .andExpect(content().string(Matchers.not(Matchers.containsString("\"contexts\""))));
        }
    }

}
