package com.nhnacademy.blog.global.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.nhnacademy.blog.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ContentSecurityPolicyFilterTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    void everyResponseHasCsp() throws Exception {
        mockMvc.perform(get("/api/no-such-path"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")));
    }

}
