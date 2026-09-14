package com.example.demo.security;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.ProblemDetails;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes an RFC 7807 body straight onto the servlet response.
 *
 * <p>Failures raised inside the filter chain — no token, a rate limit — never
 * reach a controller, so there is no message converter to render them. This
 * reuses the same body shape the controller advice produces so the client sees
 * one error format regardless of how deep the request got.
 */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String title,
                      String detail, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problemDetail = ProblemDetails.of(status, title, detail, errorCode);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ProblemDetails.asWireMap(problemDetail)));
        response.getWriter().flush();
    }
}
