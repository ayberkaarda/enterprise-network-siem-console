package com.example.demo.common;

/**
 * Raised when a caller asks for a lifecycle transition that the incident state
 * machine does not allow, for example closing an incident that was never
 * acknowledged. Surfaced to clients as HTTP 409.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
