package com.example.demo.common;

import java.time.LocalDateTime;

/**
 * Read model for audit log entries exposed by the versioned API.
 *
 * @param actor who caused the entry — an authenticated username, {@code "system"}
 *              for the unattended scheduled scan, or {@code null} for an entry
 *              written before this column existed
 */
public record AuditLogResponse(Long id, String message, LocalDateTime timestamp, String actor) {}
