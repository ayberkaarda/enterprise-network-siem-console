package com.example.demo.security;

import com.example.demo.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answer to an authenticated caller whose role does not allow the action:
 * 403 with {@code INSUFFICIENT_ROLE}.
 *
 * <p>Covers denials raised by the URL rules in the filter chain. Denials raised
 * by {@code @PreAuthorize} happen inside the dispatcher and are answered by the
 * controller advice with the same body.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter problemResponseWriter;

    public RestAccessDeniedHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        problemResponseWriter.write(response, HttpStatus.FORBIDDEN, "Insufficient role",
                "Your role does not permit this action.", ErrorCode.INSUFFICIENT_ROLE);
    }
}
