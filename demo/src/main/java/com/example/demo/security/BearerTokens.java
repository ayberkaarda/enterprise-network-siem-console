package com.example.demo.security;

/**
 * Reads a bearer credential out of an {@code Authorization} header value.
 *
 * <p>The same header arrives over two very different transports — as an HTTP
 * header on REST calls and as a STOMP header on the WebSocket CONNECT frame —
 * and both must agree on what counts as a token, so the parsing lives in one
 * place.
 */
final class BearerTokens {

    static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private BearerTokens() {}

    /** The token, or {@code null} when the header is absent or not a bearer one. */
    static String extract(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String trimmed = headerValue.trim();
        if (trimmed.length() <= PREFIX.length() || !trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = trimmed.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
