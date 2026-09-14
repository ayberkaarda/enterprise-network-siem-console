package com.example.demo.common;

/**
 * Raised when a login or a token exchange cannot be honoured: unknown user,
 * wrong password, disabled account, or a refresh token that does not verify.
 *
 * <p>All of those deliberately produce the same exception and the same message.
 * Distinguishing "no such user" from "wrong password" turns the login endpoint
 * into a way to enumerate valid usernames.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
