package com.example.demo.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates application exceptions into RFC 7807 problem responses. Every
 * response carries an {@code errorCode} extension member from {@link ErrorCode}
 * plus a {@code timestamp}, so clients can branch on a stable identifier
 * instead of parsing the human readable detail text.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidIpException.class)
    public ProblemDetail handleInvalidIpException(InvalidIpException ex) {
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "Invalid IP address", ex.getMessage(), ErrorCode.IP_VALIDATION_FAILURE);
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ProblemDetail handleDeviceNotFoundException(DeviceNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "Device not found", ex.getMessage(), ErrorCode.DEVICE_NOT_FOUND);
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ProblemDetail handleIncidentNotFoundException(IncidentNotFoundException ex) {
        return ProblemDetails.of(
                HttpStatus.NOT_FOUND, "Incident not found", ex.getMessage(), ErrorCode.INCIDENT_NOT_FOUND);
    }

    @ExceptionHandler(RuleNotFoundException.class)
    public ProblemDetail handleRuleNotFoundException(RuleNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "Rule not found", ex.getMessage(), ErrorCode.RULE_NOT_FOUND);
    }

    @ExceptionHandler(InvalidRuleConditionException.class)
    public ProblemDetail handleInvalidRuleConditionException(InvalidRuleConditionException ex) {
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "Validation failure", ex.getMessage(), ErrorCode.VALIDATION_FAILURE);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ProblemDetail handleIllegalStateTransitionException(IllegalStateTransitionException ex) {
        return ProblemDetails.of(
                HttpStatus.CONFLICT, "Illegal state transition", ex.getMessage(), ErrorCode.INVALID_STATE_TRANSITION);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentialsException(InvalidCredentialsException ex) {
        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED, "Invalid credentials", ex.getMessage(), ErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * A caller who is authenticated but not permitted.
     *
     * <p>Method security raises this from inside the dispatcher, so it would
     * otherwise be swallowed by the catch-all below and reported as a server
     * fault. It is handled explicitly here, and the wording matches what the
     * filter chain's own handler writes for the same situation.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        return ProblemDetails.of(
                HttpStatus.FORBIDDEN,
                "Insufficient role",
                "Your role does not permit this action.",
                ErrorCode.INSUFFICIENT_ROLE);
    }

    /**
     * A caller who presented no usable credential. Most of these are raised by
     * the filter chain and answered by its entry point before a controller is
     * reached; this covers the rarer case of one escaping the dispatcher.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid access token is required for this endpoint.",
                ErrorCode.AUTH_REQUIRED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetails.of(
                HttpStatus.BAD_REQUEST,
                "Validation failure",
                "İstek gövdesi doğrulamadan geçemedi!",
                ErrorCode.VALIDATION_FAILURE);

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problemDetail.setProperty("errors", fieldErrors);
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGlobalException(Exception ex) {
        return ProblemDetails.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "Sistem genelinde beklenmeyen bir hata oluştu!",
                ErrorCode.INTERNAL_ERROR);
    }
}
