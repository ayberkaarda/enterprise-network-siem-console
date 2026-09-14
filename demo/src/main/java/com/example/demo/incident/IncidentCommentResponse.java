package com.example.demo.incident;

import java.time.Instant;

/**
 * Read model for a single analyst note on an incident.
 */
public record IncidentCommentResponse(
        Long id,
        Long incidentId,
        String author,
        String body,
        Instant createdAt) {
}
