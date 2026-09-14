package com.example.demo.security;

import com.example.demo.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answer to a request that reached a protected endpoint with no usable
 * credential: 401 with {@code AUTH_REQUIRED}.
 *
 * <p>This is a REST API, so no browser login form is offered and no
 * {@code WWW-Authenticate: Basic} challenge is sent — a challenge header would
 * make the browser pop its own credential dialog over the console.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter problemResponseWriter;

    public RestAuthenticationEntryPoint(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        problemResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "Authentication required",
                "A valid access token is required for this endpoint.", ErrorCode.AUTH_REQUIRED);
    }
}
