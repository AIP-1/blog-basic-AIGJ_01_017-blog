package com.nhnacademy.blog.global.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 컨트롤러 밖(필터)에서 COM-02 모양의 오류 응답을 쓴다.
 */
@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public ErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        write(response, errorCode, ErrorResponse.of(errorCode));
    }

    public void write(HttpServletResponse response, BusinessException e) throws IOException {
        write(response, e.getErrorCode(), ErrorResponse.of(e));
    }

    private void write(HttpServletResponse response, ErrorCode errorCode, ErrorResponse body) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

}
