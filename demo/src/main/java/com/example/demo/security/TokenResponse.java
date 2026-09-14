package com.example.demo.security;

/**
 * The token pair handed back by both login and refresh.
 *
 * @param accessToken  short lived bearer credential for the API
 * @param refreshToken long lived credential, only usable against /auth/refresh
 * @param expiresIn    lifetime of the access token in seconds, so the client can
 *                     schedule a refresh without having to decode the token
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
