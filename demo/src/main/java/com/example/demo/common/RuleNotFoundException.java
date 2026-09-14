package com.example.demo.common;

/**
 * Raised when a correlation rule lookup by identifier yields no row.
 */
public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String message) {
        super(message);
    }
}
