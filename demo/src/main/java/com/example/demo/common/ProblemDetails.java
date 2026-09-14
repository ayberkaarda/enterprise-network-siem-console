package com.example.demo.common;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Single place where the RFC 7807 body of this service is shaped.
 *
 * <p>Two different layers produce error responses. Controller failures go
 * through {@link GlobalExceptionHandler} and are rendered by the message
 * converters; failures inside the security filter chain never reach a
 * controller and have to write themselves onto the raw response. Both must look
 * identical on the wire, because the browser console branches on
 * {@code errorCode} without knowing which layer answered it — so both build
 * their body here.
 */
public final class ProblemDetails {

    public static final String PROPERTY_ERROR_CODE = "errorCode";
    public static final String PROPERTY_TIMESTAMP = "timestamp";

    private ProblemDetails() {}

    public static ProblemDetail of(HttpStatus status, String title, String detail, ErrorCode errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setProperty(PROPERTY_ERROR_CODE, errorCode.name());
        problemDetail.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problemDetail;
    }

    /**
     * Flattens a problem document into the exact JSON object the message
     * converters produce for it: the standard members first, then the extension
     * members inline rather than nested under a {@code properties} key.
     */
    public static Map<String, Object> asWireMap(ProblemDetail problemDetail) {
        Map<String, Object> body = new LinkedHashMap<>();
        // The message converters fall back to "about:blank" — the RFC 7807
        // default — whenever a ProblemDetail was never given an explicit type,
        // which every problem document this service produces is. This path has
        // to make the same substitution by hand: it writes straight to the
        // servlet response instead of going through those converters, and
        // getType() is null for exactly the same documents that would have
        // triggered the converters' fallback.
        URI type = problemDetail.getType() != null ? problemDetail.getType() : URI.create("about:blank");
        body.put("type", type.toString());
        body.put("title", problemDetail.getTitle());
        body.put("status", problemDetail.getStatus());
        body.put("detail", problemDetail.getDetail());
        if (problemDetail.getInstance() != null) {
            body.put("instance", problemDetail.getInstance().toString());
        }
        Map<String, Object> properties = problemDetail.getProperties();
        if (properties != null) {
            body.putAll(properties);
        }
        return body;
    }
}
