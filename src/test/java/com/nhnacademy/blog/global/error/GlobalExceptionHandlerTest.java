package com.nhnacademy.blog.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오류 응답 모양만 본다. 보안 필터는 스텝 3에서 따로 테스트한다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ErrorTestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void validationFailureHasFieldErrors() throws Exception {
        mockMvc.perform(post("/test/errors/validate")
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
        mockMvc.perform(post("/test/errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void typeMismatchNamesTheField() throws Exception {
        mockMvc.perform(get("/test/errors/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void businessExceptionUsesItsCodeAndDetail() throws Exception {
        mockMvc.perform(get("/test/errors/suspended"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.detail.reason").value("SPAM"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void conflictCode() throws Exception {
        mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
    }

    @Test
    void unexpectedErrorHidesInternals() throws Exception {
        mockMvc.perform(get("/test/errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("secret-table"))))
                .andExpect(content().string(not(containsString("at com.nhnacademy"))));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mockMvc.perform(post("/test/errors/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    record TitleRequest(@NotBlank(message = "제목을 입력해 주세요.") String title) {
    }

    @RestController
    static class ErrorTestController {

        @PostMapping("/test/errors/validate")
        String validate(@Valid @RequestBody TitleRequest request) {
            return "ok";
        }

        @GetMapping("/test/errors/posts/{id}")
        String post(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/test/errors/suspended")
        String suspended() {
            throw new BusinessException(ErrorCode.MEMBER_SUSPENDED, Map.of("reason", "SPAM"));
        }

        @GetMapping("/test/errors/conflict")
        String conflict() {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }

        @GetMapping("/test/errors/boom")
        String boom() {
            throw new IllegalStateException("select * from secret-table");
        }

    }

}
