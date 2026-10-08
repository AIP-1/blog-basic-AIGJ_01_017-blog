package com.nhnacademy.blog.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.global.auth.CsrfHeaderFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 오류 응답 모양 (COM-02). 오류를 일으키는 API는 support.TestErrorController에 있다.
 */
class GlobalExceptionHandlerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    void validationFailureHasFieldErrors() throws Exception {
        mockMvc.perform(post("/api/test/errors/validate")
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("제목을 입력해 주세요."))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void unreadableBodyIsValidationFailedWithoutParserMessage() throws Exception {
        mockMvc.perform(post("/api/test/errors/validate")
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void typeMismatchNamesTheField() throws Exception {
        mockMvc.perform(get("/api/test/errors/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void businessExceptionUsesItsCodeAndDetail() throws Exception {
        mockMvc.perform(get("/api/test/errors/suspended"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.detail.reason").value("SPAM"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void conflictCode() throws Exception {
        mockMvc.perform(get("/api/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
    }

    @Test
    void unexpectedErrorHidesInternals() throws Exception {
        mockMvc.perform(get("/api/test/errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("secret-table"))))
                .andExpect(content().string(not(containsString("at com.nhnacademy"))));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mockMvc.perform(post("/api/test/errors/boom").header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.EXPECTED_VALUE))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

}
