package com.example.demo.exception;

public class InvalidIpException extends RuntimeException {
    public InvalidIpException(String message) {
        super(message);
    }
}