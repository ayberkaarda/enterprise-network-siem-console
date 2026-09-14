package com.example.demo.common;

/**
 * Stable, machine readable error identifiers carried in the {@code errorCode}
 * extension member of every RFC 7807 problem response. Clients map these to
 * localised messages; the human readable {@code detail} field is not a contract.
 */
public enum ErrorCode {
    DEVICE_NOT_FOUND,
    INCIDENT_NOT_FOUND,
    RULE_NOT_FOUND,
    INVALID_STATE_TRANSITION,
    IP_VALIDATION_FAILURE,
    VALIDATION_FAILURE,
    /** Username or password did not match, or a refresh token was not usable. */
    INVALID_CREDENTIALS,
    /** No usable access token was presented on an endpoint that requires one. */
    AUTH_REQUIRED,
    /** The caller is authenticated but their role does not permit the action. */
    INSUFFICIENT_ROLE,
    /** The caller exceeded the allowed request rate for the endpoint. */
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
