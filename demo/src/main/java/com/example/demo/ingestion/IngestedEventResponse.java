package com.example.demo.ingestion;

import com.example.demo.incident.Severity;
import java.time.Instant;

/**
 * Read model for a stored ingestion record.
 */
public record IngestedEventResponse(
        Long id,
        String source,
        String category,
        Severity severity,
        String rawPayload,
        Instant occurredAt,
        Instant receivedAt) {}
