package com.example.demo.common;

/**
 * Raised by the service layer when an IPv4 address does not pass validation.
 * Bean validation covers the same rule for request DTOs; this exception remains
 * for the service entry points that accept an already built entity.
 */
public class InvalidIpException extends RuntimeException {

    public InvalidIpException(String message) {
        super(message);
    }
}
