package com.example.demo.ingestion;

import com.example.demo.incident.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Write model for the event ingestion endpoint. {@code severity} and
 * {@code occurredAt} are optional; absent values default to {@code INFO} and to
 * the moment of receipt respectively.
 */
public record IngestedEventRequest(
        @NotBlank @Size(max = 255) String source,
        @NotBlank @Size(max = 255) String category,
        Severity severity,
        @Size(max = 8000) String rawPayload,
        Instant occurredAt) {}
