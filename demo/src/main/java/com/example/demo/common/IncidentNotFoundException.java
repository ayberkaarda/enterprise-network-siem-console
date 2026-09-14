package com.example.demo.common;

/**
 * Raised when an incident lookup by identifier yields no row.
 */
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String message) {
        super(message);
    }
}
