package com.example.demo.security;

/**
 * Login credentials.
 *
 * <p>Deliberately carries no bean validation constraints: an empty username or
 * password is a failed login, not a malformed request, and answering it with a
 * 400 would tell a caller that the shape of their guess was the problem. Every
 * rejected login leaves by the same 401 door.
 */
public record LoginRequest(String username, String password) {
}
