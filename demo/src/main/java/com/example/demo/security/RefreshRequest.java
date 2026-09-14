package com.example.demo.security;

/**
 * Exchange of a refresh token for a fresh token pair. As with
 * {@link LoginRequest}, a missing or empty value is a rejected credential
 * rather than a malformed request.
 */
public record RefreshRequest(String refreshToken) {
}
