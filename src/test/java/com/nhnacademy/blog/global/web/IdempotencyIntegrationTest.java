package com.nhnacademy.blog.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.global.auth.CsrfHeaderFilter;
import com.nhnacademy.blog.support.TestIdempotentController;
import com.nhnacademy.blog.support.TestMembers;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 연타 방지 (T016, R-09): 같은 키 두 번에 결과 하나.
 */
class IdempotencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestIdempotentController controller;

    @Autowired
    TestMembers testMembers;

    @Test
    void missingOrMalformedKeyIs400() throws Exception {
        int before = controller.created.get();

        mockMvc.perform(request(null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mockMvc.perform(request("not-a-uuid"))
                .andExpect(status().isBadRequest());
        assertThat(controller.created.get()).isEqualTo(before);
    }

    @Test
    void sameKeyTwiceGivesOneResult() throws Exception {
        String key = UUID.randomUUID().toString();
        int before = controller.created.get();

        MockHttpServletResponse first = mockMvc.perform(request(key))
                .andExpect(status().isCreated()).andReturn().getResponse();
        MockHttpServletResponse second = mockMvc.perform(request(key))
                .andExpect(status().isCreated()).andReturn().getResponse();

        assertThat(controller.created.get()).isEqualTo(before + 1);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(second.getHeader("Location")).isEqualTo(first.getHeader("Location"));
    }

    @Test
    void differentKeysOrMembersAreDifferentRequests() throws Exception {
        String key = UUID.randomUUID().toString();
        int before = controller.created.get();

        mockMvc.perform(request(key)).andExpect(status().isCreated());
        mockMvc.perform(request(UUID.randomUUID().toString())).andExpect(status().isCreated());
        mockMvc.perform(request(key).cookie(testMembers.loginCookies(testMembers.create())))
                .andExpect(status().isCreated());

        assertThat(controller.created.get()).isEqualTo(before + 3);
    }

    @Test
    void failedFirstRequestCanBeRetriedWithSameKey() throws Exception {
        String key = UUID.randomUUID().toString();
        int before = controller.failed.get();

        mockMvc.perform(post("/api/test/idempotent-fail").header(IdempotencyInterceptor.HEADER, key)
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/test/idempotent-fail").header(IdempotencyInterceptor.HEADER, key)
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE))
                .andExpect(status().isConflict());

        assertThat(controller.failed.get()).isEqualTo(before + 2);
    }

    @Test
    void concurrentDoubleClickRunsOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        int before = controller.created.get();
        Callable<MockHttpServletResponse> click = () -> mockMvc
                .perform(request(key).queryParam("sleepMillis", "300"))
                .andReturn().getResponse();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<MockHttpServletResponse>> results = executor.invokeAll(List.of(click, click));
            MockHttpServletResponse first = results.get(0).get();
            MockHttpServletResponse second = results.get(1).get();

            assertThat(first.getStatus()).isEqualTo(201);
            assertThat(second.getStatus()).isEqualTo(201);
            assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        } finally {
            executor.shutdown();
        }
        assertThat(controller.created.get()).isEqualTo(before + 1);
    }

    private MockHttpServletRequestBuilder request(String key) {
        MockHttpServletRequestBuilder request = post("/api/test/idempotent")
                .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE);
        return key == null ? request : request.header(IdempotencyInterceptor.HEADER, key);
    }

}
