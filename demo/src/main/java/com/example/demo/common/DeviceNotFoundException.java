package com.example.demo.common;

/**
 * Raised when a device lookup by identifier yields no row.
 */
public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(String message) {
        super(message);
    }
}
